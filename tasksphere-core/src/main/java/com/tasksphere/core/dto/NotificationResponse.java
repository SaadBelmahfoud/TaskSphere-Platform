package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Notification;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse Notification (Ce que le client reçoit)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : DTO pour les notifications temps réel
 * ─────────────────────────────────────────────────────────────────────
 *
 * UTILISÉ PAR :
 * - WebSocket : envoyé via SimpMessagingTemplate.convertAndSendToUser()
 * - REST API : GET /api/v1/notifications (historique des notifications)
 *
 * PRINCIPE DTO :
 * Comme pour TaskResponse et CommentResponse, on ne renvoie JAMAIS
 * le record domaine directement. Le DTO contrôle les champs exposés.
 */
public record NotificationResponse(
        String id,
        String type,            // INFO, WARNING, URGENT
        String title,
        String message,
        String recipientUsername,
        String relatedTaskId,
        String relatedTaskTitle,
        boolean read,
        LocalDateTime createdAt
) {

    /**
     * Convertit un objet domaine Notification en DTO de sortie.
     */
    public static NotificationResponse fromDomain(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type().name(),
                notification.title(),
                notification.message(),
                notification.recipientUsername(),
                notification.relatedTaskId(),
                notification.relatedTaskTitle(),
                notification.read(),
                notification.createdAt()
        );
    }
}