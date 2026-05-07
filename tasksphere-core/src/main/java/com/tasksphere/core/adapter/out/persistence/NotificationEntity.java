package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Notification;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : NotificationEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Entité JPA pour les notifications persistées
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE — NOTIFICATIONS PERSISTÉES :
 * Jusqu'à présent, les notifications étaient uniquement pushées via
 * WebSocket (éphémères). Cette entité permet de les stocker en base
 * pour les récupérer lors de la reconnexion et pour le compteur
 * de notifications non lues.
 *
 * MAPPING DOMAINE ↔ ENTITÉ :
 * ┌──────────────────────┬────────────────────────┐
 * │  Notification ( domaine) │  NotificationEntity (JPA) │
 * ├──────────────────────┼────────────────────────┤
 * │  id                  │  id                    │
 * │  type                │  type                  │
 * │  title               │  title                 │
 * │  message             │  message               │
 * │  recipientUsername   │  targetUsername        │
 * │  relatedTaskId       │  taskId                │
 * │  relatedTaskTitle    │  taskTitle             │
 * │  read                │  isRead                │
 * │  createdAt           │  createdAt             │
 * │  (N/A)               │  actorUsername         │
 * └──────────────────────┴────────────────────────┘
 *
 * NOTE : actorUsername est ajouté car le domaine Notification
 * ne contient pas l'acteur, mais le frontend en a besoin
 * pour l'affichage (qui a déclenché l'action).
 */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 1 : Acteur de la notification
     * ═══════════════════════════════════════════════════════════════════
     * L'utilisateur qui a déclenché l'action (ex: celui qui a assigné
     * la tâche). Peut être null pour les événements système.
     * ═══════════════════════════════════════════════════════════════════
     */
    @Column(length = 255)
    private String actorUsername;

    @Column(nullable = false, length = 255)
    private String targetUsername;

    @Column(length = 36)
    private String taskId;

    @Column(length = 255)
    private String taskTitle;

    @Column(nullable = false)
    private Boolean isRead;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected NotificationEntity() {}

    /**
     * Constructeur depuis le domaine Notification.
     *
     * NOTE : Le domaine Notification utilise recipientUsername
     * tandis que la table utilise target_username. Le mapping
     * est fait ici.
     */
    public NotificationEntity(Notification notification, String actorUsername) {
        this.id = notification.id();
        this.type = notification.type().name();
        this.title = notification.title();
        this.message = notification.message();
        this.actorUsername = actorUsername;
        this.targetUsername = notification.recipientUsername();
        this.taskId = notification.relatedTaskId();
        this.taskTitle = notification.relatedTaskTitle();
        this.isRead = notification.read();
        this.createdAt = notification.createdAt();
    }

    /**
     * Conversion vers le domaine Notification.
     *
     * NOTE : On reconstruit un objet domaine à partir de l'entité JPA.
     * Le champ actorUsername n'est PAS dans le domaine Notification
     * mais est exposé séparément dans le DTO NotificationResponse.
     */
    public Notification toDomain() {
        return new Notification(
                id,
                Notification.NotificationType.valueOf(type),
                title,
                message,
                targetUsername,
                taskId,
                taskTitle,
                isRead,
                createdAt
        );
    }

    // Getters nécessaires pour JPA et les requêtes
    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getActorUsername() { return actorUsername; }
    public String getTargetUsername() { return targetUsername; }
    public String getTaskId() { return taskId; }
    public String getTaskTitle() { return taskTitle; }
    public Boolean getIsRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters nécessaires pour les mises à jour partielles
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
}