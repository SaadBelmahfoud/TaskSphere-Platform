package com.tasksphere.iam.config.security;

/*
 * ====================================================================
 * LES IMPORTS (La gestion des dépendances)
 * ====================================================================
 * ATTENTION : En Spring Boot 3.x, on utilise JAKARTA (jakarta.servlet.*),
 * plus jamais JAVAX (javax.servlet.*). C'est une rupture obligatoire.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Imports spécifiques à la librairie JJWT (Gestion des erreurs cryptographiques)
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;

// Imports Spring & Lombok
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * ====================================================================
 * LA CLASSE (Le Filtre de Sécurité)
 * ====================================================================
 *
 * DESIGN PATTERN : Chain of Responsibility (Chaîne de responsabilité).
 * Ce filtre est le premier maillon de la chaîne de sécurité. Il intercepte TOUTES les requêtes HTTP.
 *
 * @Component : Indique à Spring que c'est un bean de configuration/infrastructure.
 * @Slf4j : Génère un logger "log" pour tracer ce qui se passe (indispensable en sécurité).
 * @RequiredArgsConstructor : Injecte les dépendances finales via le constructeur (Injection propre).
 * OncePerRequestFilter : Garantit que ce filtre ne s'exécute qu'une seule et unique fois par requête,
 *                         même si la requête est redirigée en interne par Spring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // ====================================================================
    // DÉPENDANCES
    // ====================================================================

    // Service maison qui sait lire et vérifier la cryptographie du token.
    private final JwtService jwtService;

    // Service standard de Spring Security pour charger un utilisateur depuis... n'importe où (BDD, mémoire, etc.)
    private final UserDetailsService userDetailsService;

    /*
     * ====================================================================
     * LA MÉTHODE CŒUR (L'interception)
     * ====================================================================
     * Cette méthode est appelée automatiquement par Tomcat pour CHAQUE requête HTTP entrante.
     *
     * @param request : La requête HTTP entrante (celle du client).
     * @param response : La réponse HTTP (pour renvoyer des erreurs si besoin, mais on évitera).
     * @param filterChain : L'objet qui représente le RESTE de la chaîne de filtres (le maillon suivant).
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        log.debug("FILTRE SECURITE : Interception de la requête {}", request.getRequestURI());

        // -------------------------------------------------------------
        // ÉTAPE 1 : Extraction de l'en-tête "Authorization"
        // -------------------------------------------------------------
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Si pas d'en-tête, ou s'il ne commence pas par "Bearer ", on ignore et on passe au filtre suivant.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("FILTRE SECURITE : Pas de token Bearer trouvé, on passe au filtre suivant");
            filterChain.doFilter(request, response);
            return;
        }

        // On enlève les 7 premiers caractères ("Bearer ") pour ne garder que le token JWT lui-même.
        jwt = authHeader.substring(7);

        // -------------------------------------------------------------
        // ÉTAPE 2 : Le Bouclier Anti-Crash (Try/Catch)
        // -------------------------------------------------------------
        // RÈGLE D'OR DE SÉCURITÉ : On ne laisse JAMAIS une exception de cryptographie remonter
        // jusqu'au client ou polluer les logs en erreur (ERROR). Un attaquant pourrait forger un token
        // pour provoquer des erreurs de parsing et saturer les logs (Log Forging).
        try {
            // On demande à notre service de lire le token pour trouver le username
            username = jwtService.extractUsername(jwt);

            // On vérifie si Spring a déjà authentifié quelqu'un pour cette requête
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // On demande au UserDetailsService de charger l'utilisateur correspondant
                // (Dans notre cas, ça va chercher dans le stub "UserDetailsConfig")
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // On vérifie mathématiquement que la signature du token correspond bien à cet utilisateur
                if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                    log.info("FILTRE SECURITE : Token valide pour l'utilisateur {}", username);

                    // -------------------------------------------------------------
                    // ÉTAPE 3 : La Création du Ticket d'Entrée
                    // -------------------------------------------------------------
                    // Si on arrive ici, l'utilisateur est légitime. On crée un objet d'authentification Spring.
                    // On lui donne ses droits (Authorities) récupérés depuis le UserDetails.
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null, // Pas de mot de passe ici, le JWT prouve l'identité
                            userDetails.getAuthorities()
                    );

                    // On attache des détails techniques à ce ticket (ex: l'adresse IP du client)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // -------------------------------------------------------------
                    // ÉTAPE 4 : Le Contexte de Sécurité (La Magie de Spring)
                    // -------------------------------------------------------------
                    // On place le ticket dans le "SecurityContextHolder".
                    // C'est un objet global attaché au Thread en cours (ThreadLocal).
                    // À partir de cet instant, n'importe où dans le code (ex: dans TaskController),
                    // on pourra faire "SecurityContextHolder.getContext().getAuthentication()" pour savoir qui est connecté !
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (SignatureException e) {
            // Le token a été modifié, la signature cryptographique ne correspond plus.
            // C'est une tentative d'attaque (ou un token obsolète). On log en "WARN" (avertissement) pour monitorer.
            log.warn("FILTRE SECURITE : Signature JWT invalide. Tentative de falsification ou token corrompu. Rejet silencieux.");
        } catch (ExpiredJwtException e) {
            // Le token est valide cryptographiquement, mais sa date d'expiration est dépassée.
            // C'est normal (l'utilisateur a laissé sa session expirer). On log en "WARN".
            log.warn("FILTRE SECURITE : Le token JWT a expiré. Rejet silencieux.");
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // NOUVEAU : Quelqu'un a un token valide, mais l'utilisateur a été supprimé de la base depuis.
            // On le traite comme une tentative d'intrusion ou une session obsolète.
            log.warn("FILTRE SECURITE : Token valide, mais l'utilisateur n'existe plus en base. Rejet silencieux.");
        }
        catch (Exception e) {
            // Attrape-par-tout pour les erreurs inattendues (ex: format du token complètement cassé).
            // On log en "ERROR" car c'est une anomalie technique qui nécessite l'attention de l'équipe.
            log.error("FILTRE SECURITE : Erreur inattendue lors de l'analyse du token", e);
        }

        // -------------------------------------------------------------
        // ÉTAPE 5 : Passer la main (OBLIGATOIRE)
        // -------------------------------------------------------------
        // ATTENTION : Quoi qu'il se passe (bon token, mauvais token, ou exception interceptée),
        // on DOIT appeler filterChain.doFilter().
        // Si on ne l'appelle pas, la requête meurt ici et le client aura un "Connection Reset".
        // Si le token est invalide, le SecurityContextHolder sera vide. Spring Security s'en rendra
        // un peu plus loin dans la chaîne et renverra automatiquement une réponse HTTP 401 Unauthorized.
        filterChain.doFilter(request, response);
    }
}