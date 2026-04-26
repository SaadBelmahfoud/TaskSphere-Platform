package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance du journal d'activité (Contrat du domaine)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL : Ce port est défini par le domaine (Inversion de Dépendance).
 * L'adaptateur ActivityLogPersistenceAdapter implémentera cette interface.
 *
 * PARTICULARITÉ DE L'AUDIT LOG :
 * ──────────────────────────────
 * Contrairement à TaskPersistencePort et CommentPersistencePort,
 * ce port n'a PAS de méthode save() pour mise à jour.
 * L'audit log est APPEND-ONLY : on ne fait qu'AJOUTER des entrées.
 *
 * MÉTHODES :
 * ─────────
 * 1. save() : AJOUTER une nouvelle entrée (INSERT uniquement, jamais UPDATE)
 * 2. findRecent() : récupérer les N entrées les plus récentes (pour le Dashboard)
 * 3. findAll() : récupérer toutes les entrées avec pagination (pour la page historique)
 */
public interface ActivityLogPort {

    /**
     * Enregistre une nouvelle entrée d'audit log.
     * Jamais de mise à jour : l'audit log est append-only.
     */
    ActivityLog save(ActivityLog activityLog);

    /**
     * Récupère les N entrées les plus récentes.
     * Utilisé par le Dashboard pour afficher les activités récentes.
     *
     * @param limit Nombre maximum d'entrées à retourner (ex: 10)
     * @return Liste des entrées triées par timestamp décroissant
     */
    java.util.List<ActivityLog> findRecent(int limit);

    /**
     * Récupère toutes les entrées avec pagination et filtre optionnel.
     *
     * Utilisé par l'ActivityLogController pour la page d'historique.
     *
     * @param taskId ID de la tâche pour filtrer (null = toutes les tâches)
     * @param pageable Pagination (page, size, sort)
     * @return Page d'ActivityLog
     */
    Page<ActivityLog> findAll(String taskId, Pageable pageable);
}