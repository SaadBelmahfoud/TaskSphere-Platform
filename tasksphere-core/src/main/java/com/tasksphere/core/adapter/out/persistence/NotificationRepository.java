package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : NotificationRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Repository pour les notifications persistées
 * ─────────────────────────────────────────────────────────────────────
 *
 * MÉTHODES :
 * ─────────
 * 1. findByTargetUsernameOrderByCreatedAtDesc() → Liste des notifs d'un utilisateur
 * 2. countByTargetUsernameAndIsReadFalse() → Compteur de non lues
 * 3. markAllAsReadByTargetUsername() → Tout marquer comme lu (batch)
 */
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    /**
     * Trouve toutes les notifications d'un utilisateur, triées par date décroissante.
     * Utilisé par GET /api/v1/notifications
     */
    List<NotificationEntity> findByTargetUsernameOrderByCreatedAtDesc(String targetUsername);

    /**
     * Compte les notifications non lues d'un utilisateur.
     * Utilisé par GET /api/v1/notifications/unread-count
     */
    long countByTargetUsernameAndIsReadFalse(String targetUsername);

    /**
     * Marque toutes les notifications d'un utilisateur comme lues.
     * Utilisé par POST /api/v1/notifications/read-all
     *
     * @Modifying : nécessaire pour les opérations UPDATE/DELETE en JPA
     * @Query : requête JPQL personnalisée pour le batch update
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.targetUsername = :targetUsername AND n.isRead = false")
    void markAllAsReadByTargetUsername(@Param("targetUsername") String targetUsername);
}