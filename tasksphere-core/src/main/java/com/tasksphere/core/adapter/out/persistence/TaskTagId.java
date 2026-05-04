package com.tasksphere.core.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * ═══════════════════════════════════════════════════════════════════
 * CLÉ COMPOSITE : TaskTagId (Pour @IdClass de TaskTagEntity)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE @IdClass :
 * JPA nécessite une classe séparée pour les clés composites.
 * Cette classe doit :
 * 1. Implémenter Serializable
 * 2. Redéfinir equals() et hashCode()
 * 3. Avoir les mêmes champs que @Id dans l'entité
 */
public class TaskTagId implements Serializable {

    private String taskId;
    private String tagId;

    public TaskTagId() {}

    public TaskTagId(String taskId, String tagId) {
        this.taskId = taskId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskTagId that = (TaskTagId) o;
        return Objects.equals(taskId, that.taskId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, tagId);
    }
}