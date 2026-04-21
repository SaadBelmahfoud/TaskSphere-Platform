package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.ActivityLog;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : ActivityLogEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * Entité JPA pour le journal d'audit.
 *
 * SIMPLICITÉ PAR RAPPORT À TaskEntity/CommentEntity :
 * ──────────────────────────────────────────────────
 * Pas de chemin dual, pas de dirty checking, pas de update.
 * L'audit log est APPEND-ONLY : on ne fait que des INSERT.
 * Le constructeur CommentEntity(activityLog) est le seul chemin.
 *
 * TABLE SQL : activity_logs
 * Colonnes : id, action, description, username, task_id, task_title, timestamp
 */
@Entity
@Table(name = "activity_logs")
public class ActivityLogEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** Type d'action (stocké en String, pas en enum) pour plus de flexibilité */
    @Column(nullable = false, length = 50)
    private String action;

    /** Description humaine de l'action */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Email de l'utilisateur qui a fait l'action */
    @Column(nullable = false, length = 100)
    private String username;

    /** ID de la tâche concernée (nullable pour les actions hors contexte tâche) */
    @Column(length = 36)
    private String taskId;

    /**
     * Titre de la tâche au moment de l'action (denormalized).
     * Voir la théorie dans ActivityLog.java pour pourquoi on dénormalise.
     */
    @Column(length = 255)
    private String taskTitle;

    /** Date et heure de l'action */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** Constructeur par défaut requis par JPA/Hibernate. */
    protected ActivityLogEntity() {}

    /** Constructeur de conversion Domain → Entity (INSERT uniquement). */
    public ActivityLogEntity(ActivityLog log) {
        this.id = log.id();
        this.action = log.action();
        this.description = log.description();
        this.username = log.username();
        this.taskId = log.taskId();
        this.taskTitle = log.taskTitle();
        this.timestamp = log.timestamp();
    }

    /** Convertit l'entité JPA en objet du domaine. */
    public ActivityLog toDomain() {
        return new ActivityLog(
                this.id, this.action, this.description,
                this.username, this.taskId, this.taskTitle, this.timestamp
        );
    }

    // ═══════════════════════════════════════════════════════
    // GETTERS SEULEMENT (pas de setters — append-only)
    // ═══════════════════════════════════════════════════════
    public String getId() { return id; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public String getUsername() { return username; }
    public String getTaskId() { return taskId; }
    public String getTaskTitle() { return taskTitle; }
    public LocalDateTime getTimestamp() { return timestamp; }
}