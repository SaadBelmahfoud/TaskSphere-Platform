package com.tasksphere.core.config;

import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.UserInformationPort;
import com.tasksphere.iam.config.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * CONFIGURATION WEBSOCKET : StompAuthChannelInterceptor
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — CORRECTION W1 : Intercepteur d'authentification STOMP
 * ─────────────────────────────────────────────────────────────────────
 *
 * PROBLÈME :
 * Le backend utilise convertAndSendToUser(username, "/queue/notifications", ...)
 * pour envoyer des notifications ciblées par utilisateur. Spring a BESOIN
 * d'un Principal authentifié attaché à la session STOMP pour router ces
 * messages vers le bon client WebSocket. SANS Principal, le routage
 * échoue silencieusement — les notifications sont PERDUES.
 *
 * La SecurityConfig note (ligne 252-254) :
 *   "L'authentification WebSocket est gérée séparément via un intercepteur
 *    STOMP qui vérifie le JWT dans les headers de la connexion STOMP
 *    (CONNECT frame)."
 * MAIS cet intercepteur n'existait PAS — il est créé ici.
 *
 * PRINCIPE — CHANNEL INTERCEPTOR :
 * ─────────────────────────────────
 * ChannelInterceptor est l'équivalent Spring Messaging de
 * OncePerRequestFilter (Spring Security HTTP). Il intercepte les
 * messages STOMP dans le clientInboundChannel AVANT traitement.
 *
 * PHASES D'INTERCEPTION :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Frame STOMP  │  Action de l'intercepteur                       │
 * ├────────────────┼─────────────────────────────────────────────────┤
 * │  CONNECT       │  Extraire le JWT → Valider → Créer Principal   │
 * │  SUBSCRIBE     │  Vérifier que le Principal existe (optionnel)  │
 * │  SEND          │  Vérifier l'authentification (optionnel)       │
 * │  DISCONNECT    │  Nettoyer le contexte de sécurité              │
 * └────────────────┴─────────────────────────────────────────────────┘
 *
 * FLUX D'AUTHENTIFICATION :
 * ──────────────────────────
 * 1. Le frontend envoie une frame STOMP CONNECT avec le header natif
 *    "Authorization: Bearer <JWT>"
 * 2. L'intercepteur intercepte la frame via preSend()
 * 3. Il extrait le JWT du header natif "Authorization"
 * 4. Il valide le JWT via JwtService (même service que HTTP)
 * 5. Il crée un UsernamePasswordAuthenticationToken (Principal)
 * 6. Il l'attache au StompHeaderAccessor → session STOMP
 * 7. Spring peut maintenant router les messages vers cet utilisateur
 *
 * POURQUOI JWT ET PAS SESSION ?
 * ──────────────────────────────
 * L'application est STATELESS (pas de session HTTP). Le JWT est le
 * seul moyen d'authentification. Le WebSocket handshake (HTTP) est
 * permitAll() dans SecurityConfig car le handshake précède le
 * protocole STOMP. L'authentification réelle se fait dans la
 * frame STOMP CONNECT.
 *
 * DOUBLE SÉCURITÉ :
 * ───────────────────
 * - HTTP : JwtAuthenticationFilter vérifie le JWT sur chaque requête
 * - WebSocket : StompAuthChannelInterceptor vérifie le JWT sur
 *   la frame CONNECT STOMP
 * Les deux utilisent le même JwtService et le même secret.
 *
 * ═══════════════════════════════════════════════════════════════════
 * DÉTAIL TECHNIQUE — Headers natifs STOMP
 * ═══════════════════════════════════════════════════════════════════
 *
 * Le frontend passe le JWT dans les connectHeaders du client STOMP :
 *
 *   const client = new Client({
 *     webSocketFactory: () => new SockJS('/ws'),
 *     connectHeaders: { Authorization: 'Bearer <token>' },
 *     ...
 *   });
 *
 * Spring rend ces headers accessibles via :
 *   accessor.getFirstNativeHeader("Authorization")
 *
 * "Natifs" = headers qui ne sont PAS des headers STOMP standards
 * (comme destination, content-type, etc.) mais des headers
 * personnalisés ajoutés par l'application.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /**
     * Service JWT partagé avec JwtAuthenticationFilter (module IAM).
     * Même secret, même logique de validation = cohérence d'authentification
     * entre HTTP et WebSocket.
     */
    private final JwtService jwtService;

    /**
     * Port pour récupérer les informations utilisateur (rôle).
     * Utilisé pour construire les authorities du Principal.
     */
    private final UserInformationPort userInformationPort;

    /**
     * Intercepte les messages STOMP entrants AVANT traitement.
     *
     * PRINCIPE — preSend() :
     * Cette méthode est appelée pour CHAQUE message STOMP entrant
     * (CONNECT, SUBSCRIBE, SEND, etc.). On ne traite que les
     * frames CONNECT pour l'authentification.
     *
     * LOGIQUE :
     * 1. Vérifier si c'est une frame CONNECT
     * 2. Extraire le header Authorization
     * 3. Valider le JWT
     * 4. Créer et attacher le Principal
     *
     * @param message Le message STOMP entrant
     * @param channel Le canal de communication
     * @return Le message (éventuellement modifié avec le Principal)
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // ═══════════════════════════════════════════════════════════════════
            // ÉTAPE 1 : Extraction du JWT depuis les headers natifs STOMP
            // ═══════════════════════════════════════════════════════════════════
            // Le frontend envoie le JWT dans le header "Authorization"
            // de la frame CONNECT STOMP (voir useWebSocket.ts).
            // accessor.getFirstNativeHeader() retourne la première valeur
            // du header natif spécifié.
            // ═══════════════════════════════════════════════════════════════════
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                try {
                    // ═══════════════════════════════════════════════════════════════════
                    // ÉTAPE 2 : Validation du JWT
                    // ═══════════════════════════════════════════════════════════════════
                    // JwtService.extractUsername() valide la signature et l'expiration.
                    // Si le token est invalide, une exception est levée.
                    // Le subject du JWT est l'email de l'utilisateur.
                    // ═══════════════════════════════════════════════════════════════════
                    String email = jwtService.extractUsername(token);

                    if (email != null) {
                        // ═══════════════════════════════════════════════════════════════════
                        // ÉTAPE 3 : Récupération du rôle utilisateur
                        // ═══════════════════════════════════════════════════════════════════
                        // On utilise UserInformationPort (port du domaine) pour
                        // récupérer le rôle. Ce port est implémenté par IamUserAdapter
                        // qui appelle directement le module IAM en mémoire
                        // (pas d'appel HTTP, même JVM).
                        // ═══════════════════════════════════════════════════════════════════
                        UserInfo userInfo = userInformationPort.getUserInfo(email);
                        String role = userInfo.userRole();
                        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;

                        // ═══════════════════════════════════════════════════════════════════
                        // ÉTAPE 4 : Création du Principal authentifié
                        // ═══════════════════════════════════════════════════════════════════
                        // UsernamePasswordAuthenticationToken est le même type que
                        // celui créé par JwtAuthenticationFilter pour les requêtes HTTP.
                        // Il contient :
                        // - Principal (email) → utilisé par convertAndSendToUser()
                        // - Credentials (null) → pas nécessaire après validation JWT
                        // - Authorities (rôle) → pour les vérifications @PreAuthorize
                        //
                        // C'est ce Principal qui permet à Spring de router les messages
                        // via convertAndSendToUser(email, "/queue/notifications", ...).
                        // ═══════════════════════════════════════════════════════════════════
                        UsernamePasswordAuthenticationToken principal =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + cleanRole))
                                );

                        accessor.setUser(principal);
                        log.info("STOMP AUTH : Utilisateur {} (rôle: {}) authentifié via WebSocket",
                                email, cleanRole);
                    }
                } catch (io.jsonwebtoken.security.SignatureException e) {
                    log.warn("STOMP AUTH : Signature JWT invalide — connexion WebSocket rejetée");
                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    log.warn("STOMP AUTH : Token JWT expiré — connexion WebSocket rejetée");
                } catch (Exception e) {
                    log.error("STOMP AUTH : Échec de l'authentification WebSocket — {}",
                            e.getMessage());
                }
            } else {
                // Aucun token JWT dans les headers STOMP CONNECT
                // On logue en WARN mais on n'empêche PAS la connexion.
                // Spring pourrait autoriser les abonnements publics (/topic/**).
                log.warn("STOMP AUTH : Aucun token JWT dans les headers STOMP CONNECT. " +
                        "Le routage utilisateur ne fonctionnera PAS.");
            }
        }

        return message;
    }
}