package com.tasksphere.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ═══════════════════════════════════════════════════════════════════
 * CONFIGURATION WEBSOCKET — Notifications temps réel
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Notifications temps réel via WebSocket + STOMP
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE WEBSOCKET + STOMP :
 * ─────────────────────────────
 * 1. Le frontend établit une connexion WebSocket vers /ws
 * 2. Il s'abonne à des "destinations" STOMP (comme des canaux)
 * 3. Le backend publie des messages sur ces destinations
 * 4. Tous les clients abonnés reçoivent le message EN TEMPS RÉEL
 *
 * ARCHITECTURE STOMP :
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Message Broker (Spring)                                      │
 * │                                                                │
 * │  /topic/notifications   → Canal public (broadcast)            │
 * │    → Tous les utilisateurs connectés reçoivent les messages   │
 * │                                                                │
 * │  /user/queue/notifications → Canal privé (par utilisateur)   │
 * │    → Seul l'utilisateur spécifié reçoit le message            │
 * │    → convertAndSendToUser("email", "/queue/notifications", …)│
 * └──────────────────────────────────────────────────────────────┘
 *
 * ENDPOINT STOMP : /ws
 * ──────────────────────
 * C'est le point de connexion WebSocket que le frontend utilise.
 * - withSockJS() : active le fallback SockJS si WebSocket n'est pas disponible
 * - setAllowedOriginPatterns("*") : CORS pour WebSocket (en production, restreindre)
 *
 * POURQUOI /ws ET PAS /websocket ?
 * → Convention Spring : chemin court et simple
 * → Évite les conflits avec les endpoints REST /api/v1/**
 *
 * PRINCIPE ENABLEWEBSOCKETMESSAGEBROKER :
 * ────────────────────────────────────────
 * @EnableWebSocketMessageBroker active :
 * 1. Le support WebSocket dans Spring
 * 2. Le broker de messages STOMP (en mémoire)
 * 3. L'auto-configuration de SimpMessagingTemplate
 *    (injecté dans NotificationService pour envoyer des messages)
 *
 * DIFFÉRENCE /topic VS /queue :
 * ──────────────────────────────
 * /topic/*   → Publish-Subscribe : tous les abonnés reçoivent le message
 * /queue/*   → Point-to-Point : un seul receveur par message
 * /user/*    → Préfixe pour les messages ciblés par utilisateur
 *
 * EXEMPLE D'UTILISATION CÔTÉ FRONTEND (JavaScript) :
 * ──────────────────────────────────────────────────
 * const socket = new SockJS('/ws');
 * const stompClient = Stomp.over(socket);
 * stompClient.connect({}, () => {
 *     // S'abonner aux notifications personnelles
 *     stompClient.subscribe('/user/queue/notifications', (message) => {
 *         const notification = JSON.parse(message.body);
 *         showToast(notification.description);
 *     });
 * });
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION W1 : Enregistrement du StompAuthChannelInterceptor
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 *   convertAndSendToUser(username, "/queue/notifications", ...) ne
 *   pouvait PAS router les messages car aucun Principal n'était
 *   attaché aux sessions STOMP. L'intercepteur d'authentification
 *   mentionné dans SecurityConfig (ligne 252-254) n'existait pas.
 *
 * SOLUTION :
 *   1. Créer StompAuthChannelInterceptor (nouveau fichier)
 *   2. L'enregistrer ici via configureClientInboundChannel()
 *   → Chaque frame CONNECT STOMP sera authentifiée via JWT
 *   → Le Principal sera disponible pour le routage utilisateur
 * ═══════════════════════════════════════════════════════════════════
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION W1 : Injection de l'intercepteur STOMP
     * ═══════════════════════════════════════════════════════════════════
     * StompAuthChannelInterceptor valide le JWT dans les headers de la
     * frame STOMP CONNECT et crée un Principal authentifié.
     * Sans cet intercepteur, convertAndSendToUser() ne fonctionne PAS.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /**
     * Configure le broker de messages STOMP.
     *
     * LE BROKER EN MÉMOIRE :
     * ───────────────────────
     * Spring gère les messages en mémoire (pas besoin de RabbitMQ/ActiveMQ
     * pour notre cas d'usage simple). Les messages sont routés vers les
     * clients connectés selon leur abonnement.
     *
     * APPLICATION DESTINATION PREFIX : /app
     * → Préfixe pour les messages envoyés DU client VERS le serveur
     * → Exemple : stompClient.send("/app/chat", {}, message)
     * → Nous ne l'utilisons pas beaucoup, mais c'est requis par Spring
     *
     * DESTINATION PREFIX : /topic, /queue
     * → Préfixes pour les messages envoyés DU serveur VERS les clients
     * → /topic : broadcast (tous les abonnés)
     * → /queue : point-to-point (un seul receveur)
     *
     * USER DESTINATION PREFIX : /user
     * → Préfixe pour les messages ciblés par utilisateur
     * → convertAndSendToUser("email", "/queue/notifications", payload)
     * → Le client s'abonne à /user/queue/notifications
     * → Spring route automatiquement vers le bon utilisateur
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker en mémoire pour les destinations /topic et /queue
        config.enableSimpleBroker("/topic", "/queue");

        // Préfixe pour les messages client → serveur
        config.setApplicationDestinationPrefixes("/app");

        // Préfixe pour les messages utilisateur ciblés
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Enregistre l'endpoint STOMP pour la connexion WebSocket.
     *
     * STOMP ENDPOINT : /ws
     * ─────────────────────
     * C'est l'URL que le frontend utilise pour se connecter :
     *   const socket = new SockJS('http://localhost:8080/ws');
     *   const stompClient = Stomp.over(socket);
     *
     * SOCKJS FALLBACK :
     * ──────────────────
     * withSockJS() active le support SockJS qui fournit des transports
     * alternatifs si WebSocket n'est pas disponible :
     * 1. WebSocket (prioritaire)
     * 2. HTTP streaming
     * 3. HTTP long-polling
     *
     * ALLOWED ORIGIN PATTERNS :
     * ──────────────────────────
     * En développement, on autorise toutes les origines ("*").
     * En production, il faut restreindre au domaine du frontend :
     *   .setAllowedOriginPatterns("https://tasksphere.example.com")
     *
     * CORS WEBSOCKET ≠ CORS HTTP :
     * ─────────────────────────────
     * La configuration CORS dans SecurityConfig ne s'applique PAS
     * aux connexions WebSocket. Il faut configurer le CORS ici
     * via setAllowedOriginPatterns().
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION W1 : Enregistrement de l'intercepteur STOMP
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE — configureClientInboundChannel() :
     * Cette méthode permet d'ajouter des ChannelInterceptors au canal
     * de messages STOMP entrants (client → serveur). C'est l'équivalent
     * de addFilterBefore() pour les filtres HTTP.
     *
     * FLUX D'UN MESSAGE STOMP ENTRANT :
     * ┌──────────────┐    ┌─────────────────────────┐    ┌──────────────┐
     * │  Client WS   │ →  │  clientInboundChannel    │ →  │  Broker      │
     * │  (frontend)  │    │  ┌─ StompAuthInterceptor  │    │  (routing)   │
     * │              │    │  └─ Autres intercepteurs  │    │              │
     * └──────────────┘    └─────────────────────────┘    └──────────────┘
     *
     * L'intercepteur est appelé AVANT que le message ne soit traité
     * par le broker. Pour les frames CONNECT, il authentifie
     * l'utilisateur avant que Spring n'établisse la session STOMP.
     *
     * SANS CETTE MÉTHODE :
     * - StompAuthChannelInterceptor existe mais n'est JAMAIS appelé
     * - Aucun Principal n'est attaché aux sessions STOMP
     * - convertAndSendToUser() ne peut PAS router les messages
     * - Les notifications temps réel sont PERDUES
     * ═══════════════════════════════════════════════════════════════════
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}