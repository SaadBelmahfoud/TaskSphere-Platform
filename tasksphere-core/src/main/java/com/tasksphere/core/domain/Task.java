package com.tasksphere.core.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * ====================================================================
 * OBJET DE VALEUR : TASK (Le domaine pur)
 * ====================================================================
 *
 * PRINCIPE D'IMMUABILITÉ :
 * Un record Java est immuable (pas de setters). Pour "modifier" une tâche,
 * on crée une NOUVELLE instance avec les nouvelles valeurs.
 * Cela évite les bugs de concurrence quand deux threads modifient le même objet.
 *
 * PRINCIPE DU RECORD VS CLASSE :
 * Un record génère automatiquement : constructeur, getters (title()), equals(),
 * hashCode() et toString(). C'est idéal pour les objets de valeur immuables.
 *
 * SPRINT 1 - AJOUTS :
 * - status : TODO, DOING, DONE (workflow de base)
 * - priority : LOW, MEDIUM, HIGH, CRITICAL
 * - dueDate : date d'échéance (optionnelle)
 * - userId : lien vers le propriétaire (ownership)
 * - completedAt : date de complétion (auto quand status = DONE)
 * - deletedAt : soft delete (optionnel)
 *
 * PRINCIPE DES ENUMS EMBEDED :
 * On définit les enums à l'intérieur de la classe Task pour garder le domaine
 * cohérent et auto-contenu. Ainsi, TaskStatus n'existe que dans le contexte de Task.
 */
public record Task(
        String id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        LocalDateTime completedAt,
        LocalDateTime deletedAt,
        String userId
) {

    // ============================================================
    // ENUMS (Statuts et Priorités)
    // ============================================================

    /**
     * Workflow simplifié : TODO → DOING → DONE
     *
     * PRINCIPE : L'enum représente un état fini et connu à l'avance.
     * On pourrait le rendre plus complexe (ex: CANCELLED, BLOCKED) mais
     * pour le Sprint 1, on garde 3 états simples.
     */
    public enum TaskStatus {
        TODO,       // À faire
        DOING,      // En cours
        DONE        // Terminée
    }

    /**
     * Niveaux de priorité
     *
     * PRINCIPE : Plus on ajoute de valeurs, plus le domaine est riche.
     * Mais attention à ne pas surcharger : chaque valeur doit avoir
     * un comportement spécifique dans le code (ex: tri, filtrage).
     */
    public enum TaskPriority {
        LOW,        // Basse (nice to have)
        MEDIUM,     // Moyenne (par défaut)
        HIGH,       // Haute (à faire aujourd'hui)
        CRITICAL    // Critique (bloquant)
    }

    // ============================================================
    // FACTORY METHODS (Méthodes de création)
    // ============================================================

    /**
     * Crée une nouvelle tâche avec les valeurs par défaut.
     * - status = TODO
     * - priority = MEDIUM
     * - pas de dueDate, completedAt, deletedAt
     *
     * PRINCIPE FACTORY METHOD :
     * On utilise une méthode statique plutôt que le constructeur pour :
     * 1. Donner un nom explicite à la création (create() vs new Task(...))
     * 2. Appliquer des valeurs par défaut
     * 3. Valider les paramètres si nécessaire
     * 4. Centraliser la logique de création
     */
    public static Task create(String title, String description, String userId) {
        return new Task(
                UUID.randomUUID().toString(),                // ID unique (UUID v4)
                title,
                description == null ? "" : description,    // ← NORMALISATION : null → ""
                TaskStatus.TODO,      // Par défaut : à faire
                TaskPriority.MEDIUM,   // Par défaut : priorité moyenne
                null,                  // dueDate optionnelle
                null,                  // completedAt null au départ
                null,                  // deletedAt null = tâche active
                userId                 // Propriétaire de la tâche
        );
    }

    // ============================================================
    // MÉTHODES DE MISE À JOUR (Retournent une NOUVELLE instance)
    // ============================================================

    /**
     * PRINCIPE "WITH" (fluent immutable update) :
     * Chaque méthode retourne une NOUVELLE instance de Task avec le champ modifié.
     * L'instance originale reste inchangée → thread-safe.
     * C'est le pattern "with" des records immuables.
     */

    /** Met à jour le titre et la description */
    public Task update(String title, String description) {
        return new Task(this.id, title, description, this.status, this.priority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId);
    }

    /**
     * Met à jour le statut (si passage à DONE, on set completedAt automatiquement).
     *
     * PRINCIPE D'AUTOMATISATION MÉTIER :
     * Quand une tâche passe à DONE, on enregistre automatiquement la date de complétion.
     * Cela évite que le développeur frontend doive envoyer completedAt manuellement.
     */
    public Task updateStatus(TaskStatus newStatus) {
        LocalDateTime completedAt = (newStatus == TaskStatus.DONE)
                ? LocalDateTime.now()
                : null;
        return new Task(this.id, this.title, this.description, newStatus, this.priority,
                this.dueDate, completedAt, this.deletedAt, this.userId);
    }

    /** Met à jour la priorité */
    public Task updatePriority(TaskPriority newPriority) {
        return new Task(this.id, this.title, this.description, this.status, newPriority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId);
    }

    /** Met à jour la date d'échéance */
    public Task updateDueDate(LocalDate newDueDate) {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                newDueDate, this.completedAt, this.deletedAt, this.userId);
    }

    /**
     * Soft delete : met deletedAt à maintenant (la tâche est "archivée").
     *
     * PRINCIPE DU SOFT DELETE :
     * On ne supprime JAMAIS physiquement une tâche en BDD.
     * On la marque comme "supprimée" en renseignant deletedAt.
     * Avantages :
     * - Possibilité de restaurer la tâche
     * - Traçabilité (audit trail)
     * - Intégrité référentielle (pas de cascade DELETE)
     */
    public Task softDelete() {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                this.dueDate, this.completedAt, LocalDateTime.now(), this.userId);
    }

    /** Vérifie si la tâche est supprimée (archivée) */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}