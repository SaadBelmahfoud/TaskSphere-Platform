package com.tasksphere.core.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * AGGREGAT RACINE DU DOMAINE : Task (Tâche)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Ce record Java représente le cœur de notre modèle métier.
 * Il est IMMUTABLE : chaque modification retourne une NOUVELLE instance.
 * C'est le principe clé de la programmation fonctionnelle appliquée au DDD.
 *
 * POURQUOI UN RECORD ET PAS UNE CLASSE ?
 * ────────────────────────────────────────
 * 1. IMMUTABILITÉ : Un record ne peut pas être modifié après création.
 *    → Pas d'effets de bord imprévus dans le code métier.
 *    → Thread-safe par défaut (pas besoin de synchronized).
 *    → Facile à tester : on crée un objet, on appelle une méthode,
 *      on vérifie que l'objet retourné est correct.
 *
 * 2. TRANSPARENCE RÉFÉRENTIELLE :
 *    task.update("nouveau titre", "desc") ne modifie PAS task.
 *    Il retourne une NOUVELLE instance avec le titre modifié.
 *    → f(x) = y, pas f(x) modifie x.
 *
 * 3. SÉCURITÉ : Pas de setters publics → impossible de mettre
 *    l'objet dans un état incohérent depuis l'extérieur.
 *
 * ARCHITECTURE HEXAGONALE :
 * ─────────────────────────
 * Ce record appartient au DOMAINE (cœur).
 * Il ne dépend d'aucun framework (pas de JPA, pas de Spring).
 * Les adaptateurs (TaskEntity ↔ Task) font la traduction.
 *
 * CYCLE DE VIE D'UNE TÂCHE :
 * ──────────────────────────
 * Création → TODO → DOING → DONE
 *                 ↑        ↓
 *                 └────────┘
 *
 * À tout moment : softDelete() peut archiver la tâche.
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
        String userId,        // Créateur de la tâche (email)
        String assigneeId     // Personne assignée (email, optionnel)
) {

    /**
     * Énumération des statuts possibles d'une tâche.
     * Le cycle de vie est : TODO → DOING → DONE
     *
     * NOTE : On ne valide pas les transitions ici (c'est au service
     * métier TaskManager de le faire si nécessaire).
     * Pour l'instant, on permet toute transition librement.
     */
    public enum TaskStatus {
        TODO,    // À faire
        DOING,   // En cours d'exécution
        DONE     // Terminée
    }

    /**
     * Énumération des niveaux de priorité.
     * LOW < MEDIUM < HIGH < CRITICAL
     */
    public enum TaskPriority {
        LOW,      // Basse — peut attendre
        MEDIUM,   // Moyenne — priorité normale
        HIGH,     // Haute — à traiter rapidement
        CRITICAL  // Critique — urgente, bloque d'autres tâches
    }

    // ═══════════════════════════════════════════════════════
    // FACTORY METHODS (méthodes de création)
    // ═══════════════════════════════════════════════════════
    // Ce pattern (Static Factory Method) remplace le constructeur
    // public pour donner un nom explicite à la création.

    /**
     * Crée une nouvelle tâche avec les valeurs par défaut.
     *
     * Pattern : Factory Method
     * Pourquoi pas un constructeur ? Car on veut :
     * - Générer un UUID automatiquement
     * - Mettre le statut par défaut (TODO)
     * - Mettre la priorité par défaut (MEDIUM)
     * - Gérer la description null → ""
     *
     * @param title       Le titre de la tâche (obligatoire)
     * @param description La description (peut être null → "")
     * @param userId      L'email du créateur
     * @return Une nouvelle instance Task
     */
    public static Task create(String title, String description, String userId) {
        return new Task(
                UUID.randomUUID().toString(),
                title,
                description == null ? "" : description,
                TaskStatus.TODO,        // Statut par défaut
                TaskPriority.MEDIUM,     // Priorité par défaut
                null,                    // Pas de date d'échéance
                null,                    // Pas encore terminée
                null,                    // Pas supprimée
                userId,
                null                     // Pas assignée initialement
        );
    }

    /**
     * Crée une nouvelle tâche avec un assignataire direct.
     *
     * Option A d'assignation : l'assignataire est défini à la création.
     * Cela permet à un MANAGER/ADMIN de créer une tâche
     * directement assignée à un utilisateur.
     *
     * @param title       Le titre
     * @param description La description
     * @param userId      L'email du créateur
     * @param assigneeId  L'email de la personne assignée
     * @return Une nouvelle instance Task avec assigneeId renseigné
     */
    public static Task createWithAssignee(String title, String description, String userId, String assigneeId) {
        return new Task(
                UUID.randomUUID().toString(),
                title,
                description == null ? "" : description,
                TaskStatus.TODO,
                TaskPriority.MEDIUM,
                null,
                null,
                null,
                userId,
                assigneeId    // ← Assignation directe à la création
        );
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES DE MODIFICATION (retournent une NOUVELLE instance)
    // ═══════════════════════════════════════════════════════
    // Chaque méthode retourne "new Task(...)" avec le champ
    // modifié. C'est le pattern "Wither" appliqué aux records.
    // Le record original n'est JAMAIS modifié.

    /**
     * Met à jour le titre et la description.
     * Retourne une NOUVELLE instance (immutabilité).
     */
    public Task update(String title, String description) {
        return new Task(this.id, title, description, this.status, this.priority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId, this.assigneeId);
    }

    /**
     * Change le statut de la tâche.
     * Si le nouveau statut est DONE → completedAt = maintenant.
     * Sinon → completedAt = null (annulation de complétion).
     */
    public Task updateStatus(TaskStatus newStatus) {
        LocalDateTime completedAt = (newStatus == TaskStatus.DONE)
                ? LocalDateTime.now()
                : null;
        return new Task(this.id, this.title, this.description, newStatus, this.priority,
                this.dueDate, completedAt, this.deletedAt, this.userId, this.assigneeId);
    }

    /**
     * Change la priorité de la tâche.
     */
    public Task updatePriority(TaskPriority newPriority) {
        return new Task(this.id, this.title, this.description, this.status, newPriority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId, this.assigneeId);
    }

    /**
     * Change la date d'échéance de la tâche.
     */
    public Task updateDueDate(LocalDate newDueDate) {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                newDueDate, this.completedAt, this.deletedAt, this.userId, this.assigneeId);
    }

    /**
     * Assigne la tâche à un utilisateur.
     *
     * RBAC : Cette méthode est appelée par TaskManager.assignTask()
     * qui vérifie que seul ADMIN ou MANAGER peut assigner.
     *
     * @param newAssigneeId L'email de la personne à assigner
     */
    public Task assignTo(String newAssigneeId) {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId, newAssigneeId);
    }

    /**
     * Soft delete : archive la tâche sans la supprimer physiquement.
     *
     * POURQUOI SOFT DELETE ?
     * - Récupération possible (on peut remettre deletedAt à null)
     * - Audit trail (on sait quand la tâche a été "supprimée")
     * - Conformité RGPD (conservation des données)
     * - Pas de CASCADE DELETE accidentel
     *
     * Conséquence technique : TOUTES les requêtes JPA doivent filtrer
     * WHERE deletedAt IS NULL pour ne pas retourner les tâches archivées.
     */
    public Task softDelete() {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                this.dueDate, this.completedAt, LocalDateTime.now(), this.userId, this.assigneeId);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES DE QUERIE (query methods)
    // ═══════════════════════════════════════════════════════

    /** Vérifie si la tâche est archivée (soft delete). */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Vérifie si la tâche est assignée à quelqu'un. */
    public boolean isAssigned() {
        return assigneeId != null && !assigneeId.isBlank();
    }
}