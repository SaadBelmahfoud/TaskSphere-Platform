package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : TaskTagRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Repository pour la table de jointure
 * ─────────────────────────────────────────────────
 */
@Repository
public interface TaskTagRepository extends JpaRepository<TaskTagEntity, TaskTagId> {

    /**
     * Trouver tous les IDs de tags pour une tâche.
     */
    @Query("SELECT tt.tagId FROM TaskTagEntity tt WHERE tt.taskId = :taskId")
    List<String> findTagIdsByTaskId(@Param("taskId") String taskId);

    /**
     * Supprimer l'association entre un tag et une tâche.
     */
    void deleteByTaskIdAndTagId(String taskId, String tagId);
}