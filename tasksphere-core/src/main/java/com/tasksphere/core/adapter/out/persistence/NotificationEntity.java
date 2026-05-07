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
 * │  actorUsername       │  actorUsername         │
 * │  read                │  isRead                │
 * │  createdAt           │  createdAt             │
 * └──────────────────────┴────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — actorUsername maintenant dans le domaine
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT : actorUsername n'était PAS dans le domaine Notification.
 * Le constructeur prenait un paramètre séparé String actorUsername.
 * La méthode toDomain() ne pouvait PAS mapper ce champ car il
 * n'existait pas dans le domaine → perte de données.
 *
 * APRÈS : actorUsername EST dans le domaine Notification.
 * Le constructeur obtient actorUsername depuis notification.actorUsername().
 * La méthode toDomain() mappe actorUsername depuis l'entité vers le domaine.
 * La chaîne Entity → Domain → DTO est maintenant complète.
 * ═══════════════════════════════════════════════════════════════════
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
     *
     * CORRECTION : actorUsername est maintenant obtenu depuis
     * notification.actorUsername() au lieu d'un paramètre séparé,
     * car le domaine Notification inclut désormais ce champ.
     */
    public NotificationEntity(Notification notification) {
        this.id = notification.id();
        this.type = notification.type().name();
        this.title = notification.title();
        this.message = notification.message();
        this.actorUsername = notification.actorUsername();
        this.targetUsername = notification.recipientUsername();
        this.taskId = notification.relatedTaskId();
        this.taskTitle = notification.relatedTaskTitle();
        this.isRead = notification.read();
        this.createdAt = notification.createdAt();
    }

    /**
     * Conversion vers le domaine Notification.
     *
     * CORRECTION : On mappe maintenant actorUsername depuis l'entité
     * vers le domaine, car le domaine Notification inclut ce champ.
     * Cela corrige la perte de données dans la chaîne
     * Entity → Domain → DTO.
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
                actorUsername,
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