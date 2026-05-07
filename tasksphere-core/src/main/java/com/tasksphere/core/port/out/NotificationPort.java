package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Notification;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance des notifications
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Port pour les notifications persistées
 * ─────────────────────────────────────────────────────────────────────
 *
 * MÉTHODES :
 * ─────────
 * 1. save()              : Sauvegarder une notification (création)
 * 2. findByUsername()    : Lister les notifications d'un utilisateur
 * 3. countUnread()       : Compter les notifications non lues
 * 4. findById()          : Trouver par ID (pour marquer comme lu)
 * 5. markAsRead()        : Marquer une notification comme lue
 * 6. markAllAsRead()     : Marquer toutes les notifications comme lues
 */
public interface NotificationPort {

    /** Sauvegarder une notification. */
    Notification save(Notification notification, String actorUsername);

    /** Trouver les notifications d'un utilisateur (triées par date décroissante). */
    List<Notification> findByUsername(String username);

    /** Compter les notifications non lues d'un utilisateur. */
    long countUnread(String username);

    /** Trouver une notification par ID. */
    Optional<Notification> findById(String id);

    /** Marquer une notification comme lue. */
    void markAsRead(String id);

    /** Marquer toutes les notifications d'un utilisateur comme lues. */
    void markAllAsRead(String username);
}