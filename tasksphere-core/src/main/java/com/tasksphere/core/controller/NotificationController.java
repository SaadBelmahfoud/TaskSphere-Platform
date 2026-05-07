package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.dto.NotificationResponse;
import com.tasksphere.core.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : NotificationController (API REST Notifications)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : API REST complémentaire au WebSocket
 * ─────────────────────────────────────────────────────────────────────
 *
 * POURQUOI UN CONTROLLER REST EN PLUS DU WEBSOCKET ?
 * ────────────────────────────────────────────────────
 * 1. HISTORIQUE : Le WebSocket ne garde PAS les messages si le client
 *    était déconnecté. Le REST permet de récupérer les notifications
 *    manquées lors de la reconnexion.
 * 2. PERSISTANCE : Les notifications sont stockées en base
 *    pour les utilisateurs hors ligne.
 * 3. ACTIONS : Marquer comme lu, supprimer, etc.
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — Implémentation complète des endpoints REST
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT : Seul le endpoint /health existait. Les endpoints REST
 * (liste, unread-count, mark-read, read-all) étaient annoncés
 * dans les commentaires comme "version future" mais n'étaient
 * PAS implémentés. Le frontend recevait des erreurs 404 sur :
 * - GET /api/v1/notifications
 * - GET /api/v1/notifications/unread-count
 * - PATCH /api/v1/notifications/{id}/read
 * - POST /api/v1/notifications/read-all
 *
 * APRÈS : Tous les endpoints REST sont implémentés et utilisent
 * NotificationPort pour la persistance. Les notifications sont
 * maintenant persistées par NotificationService.notifyUser() et
 * récupérables via ces endpoints REST.
 * ═══════════════════════════════════════════════════════════════════
 *
 * ENDPOINTS :
 * ──────────
 * GET    /api/v1/notifications              → Liste des notifications de l'utilisateur
 * GET    /api/v1/notifications/unread-count  → Nombre de notifications non lues
 * PATCH  /api/v1/notifications/{id}/read     → Marquer une notification comme lue
 * POST   /api/v1/notifications/read-all      → Marquer toutes les notifications comme lues
 * GET    /api/v1/notifications/health        → Vérifie que le service est actif
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /**
     * ═══════════════════════════════════════════════════════════════════
     * CORRECTION PHASE 3 : Injection du NotificationPort
     * ═══════════════════════════════════════════════════════════════════
     * NotificationPort permet d'accéder aux notifications persistées
     * pour les endpoints REST (liste, compteur, marquage comme lu).
     * Sans cette injection, les endpoints ne pourraient pas fonctionner.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final NotificationPort notificationPort;

    /**
     * GET /api/v1/notifications
     *
     * Liste toutes les notifications de l'utilisateur courant,
     * triées par date décroissante (plus récentes en premier).
     *
     * UTILISÉ PAR : NotificationBell.tsx (dropdown de notifications)
     */
    @GetMapping
    public ResponseEntity<?> listNotifications(Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : GET /notifications — {}", username);

        List<Notification> notifications = notificationPort.findByUsername(username);
        List<NotificationResponse> response = notifications.stream()
                .map(NotificationResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/notifications/unread-count
     *
     * Retourne le nombre de notifications non lues pour l'utilisateur courant.
     * Le frontend utilise cette valeur pour afficher le badge de notification.
     *
     * UTILISÉ PAR : NotificationBell.tsx (badge rouge avec le nombre)
     *
     * NOTE : Le nom du champ retourné est "unreadCount" (et non "count")
     * pour être explicite et éviter la confusion avec d'autres compteurs.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        String username = authentication.getName();
        log.debug("CONTROLLER : GET /notifications/unread-count — {}", username);

        long unreadCount = notificationPort.countUnread(username);
        return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
    }

    /**
     * PATCH /api/v1/notifications/{id}/read
     *
     * Marque une notification spécifique comme lue.
     * Vérifie que la notification appartient bien à l'utilisateur courant
     * (sécurité : un utilisateur ne peut pas marquer la notification d'un autre).
     *
     * UTILISÉ PAR : NotificationBell.tsx (clic sur une notification)
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String id, Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : PATCH /notifications/{}/read — {}", id, username);

        // Vérifier que la notification existe et appartient à l'utilisateur
        var notificationOpt = notificationPort.findById(id);
        if (notificationOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "Notification non trouvée"));
        }

        Notification notification = notificationOpt.get();
        if (!notification.recipientUsername().equals(username)) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Vous n'êtes pas autorisé à modifier cette notification"));
        }

        notificationPort.markAsRead(id);
        return ResponseEntity.ok(Map.of("message", "Notification marquée comme lue"));
    }

    /**
     * POST /api/v1/notifications/read-all
     *
     * Marque TOUTES les notifications de l'utilisateur courant comme lues.
     * Utile pour le bouton "Tout marquer comme lu" dans le dropdown.
     *
     * UTILISÉ PAR : NotificationBell.tsx (bouton "Mark all read")
     */
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : POST /notifications/read-all — {}", username);

        notificationPort.markAllAsRead(username);
        return ResponseEntity.ok(Map.of("message", "Toutes les notifications marquées comme lues"));
    }

    /**
     * GET /api/v1/notifications/health
     *
     * Endpoint de santé pour vérifier que le service de notifications
     * est actif. Utile pour le monitoring et le debugging.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health(Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : GET /notifications/health — {}", username);

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "websocketEndpoint", "/ws",
                "subscribeDestination", "/user/queue/notifications",
                "username", username
        ));
    }
}