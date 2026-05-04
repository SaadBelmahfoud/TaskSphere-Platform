package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.TaskChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance de l'historique des changements
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : Port pour l'audit trail détaillé
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE DDD : Le domaine définit SON contrat de persistance.
 * L'adaptateur TaskChangeLogPersistenceAdapter implémentera cette interface.
 *
 * MÉTHODES :
 * ─────────
 * 1. save()          : Enregistrer un changement (APPEND-ONLY, jamais UPDATE)
 * 2. saveAll()       : Enregistrer plusieurs changements en batch
 * 3. findByTaskId()  : Récupérer l'historique d'une tâche avec pagination
 *
 * NOTE : Pas de méthode delete() car l'historique est APPEND-ONLY.
 * Pas de méthode update() car les entrées sont IMMUTABLES.
 */
public interface TaskChangeLogPort {

    /**
     * Enregistre une entrée de changement.
     * Append-only : jamais de mise à jour.
     */
    TaskChangeLog save(TaskChangeLog changeLog);

    /**
     * Enregistre plusieurs entrées de changement en batch.
     *
     * PRINCIPE BATCH INSERT :
     * Quand une tâche est modifiée sur plusieurs champs à la fois
     * (ex: titre + priorité), on insère tous les changements en une
     * seule opération pour des raisons de performance.
     *
     * @param changeLogs Les changements à enregistrer
     */
    void saveAll(java.util.List<TaskChangeLog> changeLogs);

    /**
     * Récupère l'historique des changements d'une tâche avec pagination.
     *
     * UTILISATION : GET /api/v1/tasks/{id}/changes
     *
     * @param taskId   L'ID de la tâche
     * @param pageable Pagination (trié par changedAt DESC par défaut)
     * @return Une page de TaskChangeLog
     */
    Page<TaskChangeLog> findByTaskId(String taskId, Pageable pageable);
}