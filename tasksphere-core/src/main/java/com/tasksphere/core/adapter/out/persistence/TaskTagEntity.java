package com.tasksphere.core.adapter.out.persistence;

import jakarta.persistence.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TaskTagEntity (Table de jointure)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Entité JPA pour la relation Many-to-Many
 * ─────────────────────────────────────────────────
 *
 * PRINCIPE — MANY-TO-MANY via table de jointure explicite :
 * Au lieu d'utiliser @ManyToMany avec @JoinTable (qui masque la table
 * de jointure), on crée une entité explicite. Cela permet :
 * 1. De requêter directement la table de jointure
 * 2. D'ajouter des champs supplémentaires plus tard (ex: date d'association)
 * 3. De contrôler finement les opérations (add/remove)
 *
 * CLÉ COMPOSITE : (taskId, tagId)
 * Un tag ne peut être appliqué qu'une seule fois à une tâche.
 */
@Entity
@Table(name = "task_tags")
@IdClass(TaskTagId.class)
public class TaskTagEntity {

    @Id
    @Column(length = 36)
    private String taskId;

    @Id
    @Column(length = 36)
    private String tagId;

    protected TaskTagEntity() {}

    public TaskTagEntity(String taskId, String tagId) {
        this.taskId = taskId;
        this.tagId = tagId;
    }

    public String getTaskId() { return taskId; }
    public String getTagId() { return tagId; }
}