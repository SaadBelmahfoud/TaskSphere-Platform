package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : TaskChangeLogRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : Repository pour l'historique détaillé
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE : Spring Data JPA génère l'implémentation automatiquement.
 * On n'écrit PAS de classe d'implémentation.
 */
@Repository
public interface TaskChangeLogRepository extends JpaRepository<TaskChangeLogEntity, String> {

    /**
     * Récupère l'historique des changements d'une tâche, paginé.
     * Trié par date décroissante (plus récent en premier).
     *
     * SQL généré :
     * SELECT * FROM task_change_logs
     * WHERE task_id = ?
     * ORDER BY changed_at DESC
     * LIMIT ? OFFSET ?
     */
    Page<TaskChangeLogEntity> findByTaskIdOrderByChangedAtDesc(String taskId, Pageable pageable);
}