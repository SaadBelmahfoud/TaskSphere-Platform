package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : Notification (Notification temps réel)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Représentation d'une notification temps réel
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE — NOTIFICATION vs ACTIVITY LOG :
 * ──────────────────────────────────────────
 * ActivityLog : enregistrement PERSISTANT de TOUTES les actions (append-only)
 *   → Consultable a posteriori (historique, audit, conformité)
 *   → Stocké en base de données
 *
 * Notification : message ÉPHÉMÈRE envoyé en TEMPS RÉEL à un utilisateur
 *   → Disparaît une fois lu ou après un délai
 *   → Peut être stocké temporairement pour les utilisateurs hors ligne
 *   → Transporté via WebSocket
 *
 * POURQUOI UN RECORD SÉPARÉ ET PAS RÉUTILISER ACTIVITYLOG ?
 * ────────────────────────────────────────────────────────────
 * 1. SRP (Single Responsibility) : ActivityLog = audit, Notification = UI temps réel
 * 2. Champs différents : Notification a `read` (lu/non-lu), `type` (INFO/WARNING/URGENT)
 * 3. Cycle de vie différent : Notification peut être supprimée, ActivityLog est immutable
 * 4. Destinataire : Notification a un `recipientUsername`, ActivityLog a un `username` (acteur)
 *
 * TYPES DE NOTIFICATIONS :
 * ────────────────────────
 * - INFO : information générale (tâche créée, commentaire ajouté)
 * - WARNING : attention requise (tâche en retard, échéance proche)
 * - URGENT : action immédiate nécessaire (tâche critique assignée)
 *
 * ARCHITECTURE HEXAGONALE :
 * ─────────────────────────
 * Ce record est dans le DOMAINE (cœur). Pas de dépendance framework.
 * Les notifications sont envoyées via le port NotificationPort
 * et l'adaptateur WebSocketNotificationAdapter.
 */
public record Notification(
        String id,                  // UUID unique
        NotificationType type,      // INFO, WARNING, URGENT
        String title,               // Titre court de la notification
        String message,             // Description détaillée
        String recipientUsername,    // Email du destinataire
        String relatedTaskId,       // ID de la tâche concernée (optionnel)
        String relatedTaskTitle,    // Titre de la tâche (denormalized, optionnel)
        boolean read,               // Si la notification a été lue
        LocalDateTime createdAt     // Date de création
) {

    /**
     * Énumération des types de notifications.
     *
     * PRINCIPE : Chaque type a un niveau d'urgence différent.
     * Le frontend peut afficher des styles différents selon le type :
     * - INFO : bleu/gris → toast discret
     * - WARNING : orange/ambre → toast avec icône ⚠️
     * - URGENT : rouge → toast persistant + son
     */
    public enum NotificationType {
        INFO,       // Information générale
        WARNING,    // Attention requise
        URGENT      // Action immédiate nécessaire
    }

    /**
     * Factory Method : Crée une nouvelle notification.
     *
     * @param type              Le type de notification
     * @param title             Titre court (ex: "Nouvelle tâche assignée")
     * @param message           Description (ex: "La tâche 'Bug fix' vous a été assignée par admin@tasksphere.com")
     * @param recipientUsername Email du destinataire
     * @param relatedTaskId     ID de la tâche concernée (null si pas de tâche)
     * @param relatedTaskTitle  Titre de la tâche (null si pas de tâche)
     * @return Une nouvelle instance Notification
     */
    public static Notification create(NotificationType type, String title, String message,
                                      String recipientUsername, String relatedTaskId, String relatedTaskTitle) {
        return new Notification(
                UUID.randomUUID().toString(),
                type,
                title,
                message,
                recipientUsername,
                relatedTaskId,
                relatedTaskTitle,
                false,  // Non lue par défaut
                LocalDateTime.now()
        );
    }

    /**
     * Marque la notification comme lue.
     * Retourne une NOUVELLE instance (immutabilité).
     */
    public Notification markAsRead() {
        return new Notification(id, type, title, message, recipientUsername,
                relatedTaskId, relatedTaskTitle, true, createdAt);
    }
}