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
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — Noms de champs alignés sur le frontend et la DB
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 * Les noms de champs du DTO ne correspondaient PAS aux noms de colonnes
 * de la table notifications ni aux noms attendus par le frontend :
 * - recipientUsername → la DB utilise target_username → le frontend attend targetUsername
 * - relatedTaskId    → la DB utilise task_id          → le frontend attend taskId
 * - relatedTaskTitle → la DB utilise task_title        → le frontend attend taskTitle
 * - actorUsername    → MANQUANT                        → le frontend en a besoin
 *
 * SOLUTION :
 * Renommage des champs pour correspondre au frontend et à la DB,
 * et ajout du champ actorUsername.
 *
 * MAPPING DTO ↔ DB :
 * ┌──────────────────────┬────────────────────────┬─────────────────────┐
 * │  DTO (NotificationResponse) │  DB Column          │  Frontend Type      │
 * ├──────────────────────┼────────────────────────┼─────────────────────┤
 * │  id                  │  id                    │  id: string          │
 * │  type                │  type                  │  type: string        │
 * │  title               │  title                 │  title: string       │
 * │  message             │  message               │  message: string     │
 * │  targetUsername      │  target_username       │  targetUsername      │
 * │  taskId              │  task_id               │  taskId: string|null │
 * │  taskTitle           │  task_title            │  (frontend l'utilise)│
 * │  actorUsername       │  actor_username        │  actorUsername       │
 * │  read                │  is_read               │  read: boolean       │
 * │  createdAt           │  created_at            │  createdAt: string   │
 * └──────────────────────┴────────────────────────┴─────────────────────┘
 * ═══════════════════════════════════════════════════════════════════
 */
public record NotificationResponse(
        String id,
        String type,                // INFO, WARNING, URGENT
        String title,
        String message,

        /**
         * ═══════════════════════════════════════════════════════════════════
         * CORRECTION : Renommé de recipientUsername → targetUsername
         * ═══════════════════════════════════════════════════════════════════
         * La table DB utilise target_username, le frontend attend targetUsername.
         * L'ancien nom "recipientUsername" était incohérent avec les deux.
         * ═══════════════════════════════════════════════════════════════════
         */
        String targetUsername,

        /**
         * ═══════════════════════════════════════════════════════════════════
         * CORRECTION : Renommé de relatedTaskId → taskId
         * ═══════════════════════════════════════════════════════════════════
         * La table DB utilise task_id, le frontend attend taskId.
         * ═══════════════════════════════════════════════════════════════════
         */
        String taskId,

        /**
         * ═══════════════════════════════════════════════════════════════════
         * CORRECTION : Renommé de relatedTaskTitle → taskTitle
         * ═══════════════════════════════════════════════════════════════════
         * La table DB utilise task_title, le frontend attend taskTitle.
         * ═══════════════════════════════════════════════════════════════════
         */
        String taskTitle,

        /**
         * ═══════════════════════════════════════════════════════════════════
         * CORRECTION : Ajout du champ actorUsername
         * ═══════════════════════════════════════════════════════════════════
         * Le frontend a besoin de savoir QUI a déclenché l'action pour
         * l'affichage dans la notification (ex: "admin@tasksphere.com
         * vous a assigné une tâche"). Ce champ était absent du DTO.
         * ═══════════════════════════════════════════════════════════════════
         */
        String actorUsername,

        boolean read,
        LocalDateTime createdAt
) {

    /**
     * Convertit un objet domaine Notification en DTO de sortie.
     *
     * CORRECTION : Mapping mis à jour pour utiliser les nouveaux noms
     * de champs (targetUsername, taskId, taskTitle, actorUsername).
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
                notification.actorUsername(),
                notification.read(),
                notification.createdAt()
        );
    }
}