package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : ActivityLog (Journal d'activité / Audit Log)
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — AUDIT LOG :
 * ──────────────────────────
 * Un audit log enregistre TOUTES les actions significatives du système.
 * C'est indispensable en entreprise pour :
 * - TRAÇABILITÉ : qui a fait quoi, quand, sur quelle ressource ?
 * - CONFORMITÉ : RGPD, SOX, HIPAA exigent un journal d'audit
 * - DÉBOGAGE : comprendre ce qui s'est passé en cas de problème
 * - ANALYTIQUE : le Dashboard utilise les activités récentes
 *
 * PROPRIÉTÉS CLÉS DE L'AUDIT LOG :
 * ────────────────────────────────
 * 1. APPEND-ONLY : On ne modifie JAMAIS un log existant.
 *    Pas de updateContent(), pas de softDelete().
 *    Si une erreur s'est produite, on ajoute un NOUVEAU log de correction.
 *
 * 2. IMMUTABLE : Comme Task et Comment, c'est un record Java.
 *    Une fois créé, un ActivityLog ne change PLUS.
 *
 * 3. ASYNCHRONE IDÉAL : En production, on utiliserait un message broker
 *    (Kafka, RabbitMQ) pour écrire les logs de façon asynchrone.
 *    Ici, on écrit de façon synchrone dans la même transaction.
 *
 * NOUVEAU CONCEPT — ENUM DES ACTIONS :
 * ──────────────────────────────
 * Chaque action est typée par une enum. Le Dashboard frontend utilise
 * ces types pour afficher des icônes différentes par action.
 *
 * ACTIONS LIÉES AUX TÂCHES :
 * - TASK_CREATED : nouvelle tâche créée
 * - TASK_UPDATED : titre, description, priorité ou dueDate modifiés
 * - TASK_STATUS_CHANGED : statut modifié (TODO→DOING, DOING→DONE, etc.)
 * - TASK_ASSIGNED : tâche assignée à un utilisateur
 * - TASK_DELETED : tâche soft-deleted
 *
 * ACTIONS LIÉES AUX COMMENTAIRES :
 * - COMMENT_ADDED : nouveau commentaire sur une tâche
 * - COMMENT_UPDATED : contenu d'un commentaire modifié
 * - COMMENT_DELETED : commentaire supprimé
 *
 * ACTIONS LIÉES À L'ADMINISTRATION :
 * - USER_ROLE_CHANGED : rôle d'un utilisateur modifié par un ADMIN
 * - USER_TOGGLED : utilisateur activé/désactivé par un ADMIN
 *
 * ARCHITECTURE HEXAGONALE :
 * ─────────────────────────
 * Ce record est dans le DOMAINE (cœur). Pas de dépendance framework.
 */
public record ActivityLog(
        String id,              // UUID unique du log
        String action,          // Type d'action (enum Action)
        String description,     // Description lisible de l'action
        String username,        // Email de l'utilisateur qui a fait l'action
        String taskId,          // ID de la tâche concernée (null si pas de tâche)
        String taskTitle,       // Titre de la tâche au moment de l'action (denormalized)
        LocalDateTime timestamp // Date et heure de l'action
) {

    /**
     * Énumération de toutes les actions traquées par l'audit log.
     * Utilisée par le Dashboard frontend pour afficher des icônes et
     * des couleurs différentes selon le type d'action.
     */
    public enum Action {
        // Actions liées aux tâches
        TASK_CREATED,            // Nouvelle tâche créée
        TASK_UPDATED,            // Tâche modifiée (titre, description, priorité, dueDate)
        TASK_STATUS_CHANGED,     // Changement de statut (TODO → DOING → DONE)
        TASK_ASSIGNED,           // Tâche assignée à un utilisateur
        TASK_DELETED,            // Tâche supprimée (soft delete)

        // Actions liées aux commentaires
        COMMENT_ADDED,           // Nouveau commentaire ajouté
        COMMENT_UPDATED,         // Commentaire modifié
        COMMENT_DELETED,         // Commentaire supprimé

        // Actions liées à l'administration
        USER_ROLE_CHANGED,       // Rôle d'un utilisateur modifié
        USER_TOGGLED             // Utilisateur activé/désactivé
    }

    // ═══════════════════════════════════════════════════════
    // FACTORY METHOD : Création d'une entrée d'audit
    // ═══════════════════════════════════════════════════════

    /**
     * Crée une nouvelle entrée d'audit log.
     *
     * @param action      Le type d'action (de l'enum Action)
     * @param description Description humaine de l'action
     *                    Exemple : "Tâche 'Bug fix' créée" ou "Statut changé en DONE"
     * @param username    Email de l'utilisateur qui a fait l'action
     * @param taskId      ID de la tâche concernée (null si hors contexte tâche,
     *                    par exemple USER_ROLE_CHANGED)
     * @param taskTitle   Titre de la tâche (pour affichage sans requête supplémentaire,
     *                    c'est du "denormalized data" — on duplique l'info pour la perf)
     * @return Une nouvelle instance ActivityLog
     *
     * POURQUOI taskTitle EST DENORMALIZED ?
     * → Si on stockait uniquement taskId, il faudrait faire un JOIN ou une
     *   requête supplémentaire pour afficher le titre dans le Dashboard.
     *   En dupliquant le titre ici (denormalization), on peut afficher
     *   l'activité directement sans JOIN. C'est un compromis classique :
     *   plus de stockage → moins de requêtes → meilleur temps de réponse.
     */
    public static ActivityLog create(Action action, String description,
                                     String username, String taskId, String taskTitle) {
        return new ActivityLog(
                UUID.randomUUID().toString(),
                action.name(),  // On stocke le nom de l'enum en String (plus flexible en BDD)
                description,
                username,
                taskId,
                taskTitle,
                LocalDateTime.now()
        );
    }
}