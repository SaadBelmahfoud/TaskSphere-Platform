package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.TaskChangeLog;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TaskChangeLogEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : Entité JPA pour l'historique détaillé
 * ─────────────────────────────────────────────────────────────────────
 *
 * MAPPING Domain ↔ Entity :
 * ┌──────────────────────────────────────────────────────────────┐
 * │  TaskChangeLog (record)     │ TaskChangeLogEntity (JPA)       │
 * │  Immutable                  │ Mutable (JPA)                   │
 * │  Pas d'annotations JPA      │ @Entity, @Table, @Id            │
 * └──────────────────────────────────────────────────────────────┘
 *
 * CORRECTION V6 : columnDefinition explicite pour PostgreSQL
 * Même correction que pour TaskEntity : VARCHAR au lieu de bytea.
 */
@Entity
@Table(name = "task_change_logs")
public class TaskChangeLogEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String taskId;

    @Column(nullable = false, length = 50)
    private String fieldName;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false, length = 255)
    private String changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    /**
     * Constructeur par défaut requis par JPA/Hibernate.
     */
    protected TaskChangeLogEntity() {}

    /**
     * Constructeur de conversion Domain → Entity.
     */
    public TaskChangeLogEntity(TaskChangeLog changeLog) {
        this.id = changeLog.id();
        this.taskId = changeLog.taskId();
        this.fieldName = changeLog.fieldName();
        this.oldValue = changeLog.oldValue();
        this.newValue = changeLog.newValue();
        this.changedBy = changeLog.changedBy();
        this.changedAt = changeLog.changedAt();
    }

    /**
     * Convertit l'entité JPA en objet du domaine (TaskChangeLog record).
     */
    public TaskChangeLog toDomain() {
        return new TaskChangeLog(id, taskId, fieldName, oldValue, newValue, changedBy, changedAt);
    }
}