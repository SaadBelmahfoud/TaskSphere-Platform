package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : ActivityLogRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * Requêtes spécifiques pour l'audit log :
 * - findRecent : les N entrées les plus récentes (pour Dashboard)
 * - findAllWithFilter : pagination avec filtre optionnel par taskId
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, String> {

    /**
     * Récupère les N entrées les plus récentes (triées par timestamp descendant).
     * Utilisé par : DashboardController pour les activités récentes.
     */
    @Query("SELECT a FROM ActivityLogEntity a ORDER BY a.timestamp DESC")
    List<ActivityLogEntity> findRecent(@Param("limit") int limit, Pageable pageable);

    /**
     * Récupère toutes les entrées avec filtre optionnel par taskId.
     * Utilisé par : ActivityLogController pour la page d'historique.
     *
     * Le pattern (:taskId IS NULL OR ...) permet de ne pas filtrer
     * quand taskId est null (voir la théorie dans TaskRepository).
     */
    @Query("SELECT a FROM ActivityLogEntity a " +
            "WHERE (:taskId IS NULL OR a.taskId = :taskId) " +
            "ORDER BY a.timestamp DESC")
    Page<ActivityLogEntity> findAllWithFilter(
            @Param("taskId") String taskId,
            Pageable pageable
    );
}