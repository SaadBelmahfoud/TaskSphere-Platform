package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/*
 * ====================================================================
 * PORT SORTANT : Persistance des tâches (Contrat du domaine)
 * ====================================================================
 *
 * PRINCIPE DDD (Domain-Driven Design) :
 * Le domaine définit SON contrat. L'infrastructure (JPA, SQL) doit s'adapter.
 * C'est l'inversion de dépendance : le domaine dicte ses besoins, l'adapter obéit.
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * Un "Port" est une interface Java que le domaine expose.
 * - Port ENTRANT (in) : ce que le domaine offre (ex: Use Case interface)
 * - Port SORTANT (out) : ce dont le domaine a besoin (ex: persistance, messaging)
 *
 * ICI : C'est un port SORTANT car le domaine a besoin de sauvegarder/lire des tâches.
 * L'adaptateur (TaskPersistenceAdapter) implémentera cette interface.
 *
 * AVANTAGES DE CETTE APPROCHE :
 * 1. TESTABILITÉ : On peut mocker cette interface dans les tests unitaires
 * 2. FLEXIBILITÉ : On peut changer d'implémentation (JPA → MongoDB → Redis) sans toucher le domaine
 * 3. INDÉPENDANCE : Le domaine ne dépend d'aucune technologie spécifique
 *
 * SPRINT 1 : Ajout de findById, update, et filtrage par utilisateur.
 * SPRINT 2 : Ajout de searchTasks avec critères dynamiques (Parameter Object Pattern).
 * SPRINT 3 (Section 6) : Ajout des méthodes de comptage pour le Dashboard.
 */
public interface TaskPersistencePort {

    /** Sauvegarder une tâche (création ou mise à jour) */
    Task save(Task task);

    /**
     * Récupérer toutes les tâches actives d'un utilisateur avec pagination.
     * "Actives" = deletedAt IS NULL (soft delete filter).
     */
    Page<Task> findByUserId(String userId, Pageable pageable);

    /** Récupérer une tâche active par son ID (deletedAt IS NULL) */
    Optional<Task> findById(String id);

    /**
     * Récupérer une tâche active par ID et par utilisateur (pour vérifier l'ownership).
     * Combine les deux filtres : id = ? AND userId = ? AND deletedAt IS NULL
     */
    Optional<Task> findByIdAndUserId(String id, String userId);

    /** Supprimer logiquement une tâche (soft delete : set deletedAt = now) */
    void softDelete(String id);

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE (Sprint 2)
    // ═══════════════════════════════════════════════════════

    /**
     * Recherche dynamique avec filtres optionnels.
     *
     * PRINCIPE PARAMETER OBJECT :
     * Au lieu de passer 9 paramètres (keyword, userId, assigneeId, status,
     * priority, dueDateFrom, dueDateTo, createdFrom, createdTo), on encapsule
     * tous les critères dans un record TaskSearchCriteria.
     *
     * AVANTAGES DU PARAMETER OBJECT :
     * 1. LISIBILITÉ : La signature est propre (1 paramètre au lieu de 9)
     * 2. EXTENSIBILITÉ : Ajouter un filtre = ajouter un champ au record
     * 3. IMMUTABILITÉ : Un record est immutable → pas d'effets de bord
     * 4. TYPAGE FORT : Chaque critère a son type (String, enum, LocalDate, etc.)
     *
     * @param criteria Les critères de recherche (tous optionnels)
     * @param pageable La pagination (page, size, sort)
     * @return Une page de tâches correspondant aux critères
     */
    Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable);

    // ═══════════════════════════════════════════════════════
    // PARAMETER OBJECT : TaskSearchCriteria
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PARAMETER OBJECT : TaskSearchCriteria
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE : Parameter Object Pattern
     * Quand une méthode a trop de paramètres (ici 9), on les regroupe
     * dans un record dédié. C'est un refactoring classique.
     *
     * POURQUOI UN INNER RECORD (dans l'interface) ?
     * - Cohérence : ce record n'a de sens QUE pour ce port
     * - Encapsulation : il n'est visible que via TaskPersistencePort.TaskSearchCriteria
     * - Simplicité : pas besoin d'un fichier séparé pour un record de 10 lignes
     *
     * FILTRES DISPONIBLES (9 filtres + pagination) :
     * ────────────────────────────────────────────
     * 1. keyword     → Recherche textuelle (titre OU description)
     * 2. userId      → Filtrer par créateur (pour ADMIN/MANAGER)
     * 3. assigneeId  → Filtrer par assignataire
     * 4. status      → Filtrer par statut (TODO/DOING/DONE)
     * 5. priority    → Filtrer par priorité (LOW/MEDIUM/HIGH/CRITICAL)
     * 6. dueDateFrom → Date d'échéance minimum (>=)
     * 7. dueDateTo   → Date d'échéance maximum (<=)
     * 8. createdFrom → Date de création minimum (>=)
     * 9. createdTo   → Date de création maximum (<=)
     *
     * UTILISATION DANS LE CONTROLLER (TaskController.getTasks()) :
     * new TaskPersistencePort.TaskSearchCriteria(keyword, null, assigneeId, ...)
     * → Le userId est null car il est injecté par le service selon le rôle
     *    (RBAC : USER ne voit que ses tâches, ADMIN/MANAGER voient tout)
     *
     * UTILISATION DANS LE SERVICE (TaskManager.searchTasks()) :
     * → ADMIN/MANAGER : les critères passés tels quels (recherche globale)
     * → USER : deux recherches sont faites (owned + assigned) puis fusionnées
     */
    record TaskSearchCriteria(
            String keyword,        // Recherche textuelle (titre ou description)
            String userId,         // Filtrer par créateur (injecté par RBAC)
            String assigneeId,     // Filtrer par assignataire
            Task.TaskStatus status,        // Filtrer par statut
            Task.TaskPriority priority,    // Filtrer par priorité
            LocalDate dueDateFrom,         // Date d'échéance minimum
            LocalDate dueDateTo,           // Date d'échéance maximum
            LocalDateTime createdFrom,     // Date de création minimum
            LocalDateTime createdTo        // Date de création maximum
    ) {}

    // ═══════════════════════════════════════════════════════
    // STATISTIQUES POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * MÉTHODES DE COMPTAGE — Section 6 : Dashboard & Collaboration
     * ═══════════════════════════════════════════════════════════
     *
     * POURQUOI DES MÉTHODES DE COMPTAGE SPÉCIFIQUES ?
     * ────────────────────────────────────────────
     * Le DashboardService doit afficher des KPI :
     * - Nombre total de tâches
     * - Nombre de tâches par statut (TODO, DOING, DONE)
     * - Nombre de tâches en retard (dueDate < now ET status ≠ DONE)
     * - Répartition par priorité
     *
     * SANS ces méthodes, le DashboardService devrait :
     * 1. Charger TOUTES les tâches en mémoire (SELECT * FROM tasks)
     * 2. Itérer en Java pour compter (task.status() == TODO ? count++ : ...)
     *
     * AVEC ces méthodes, on délègue le COUNT à la BDD :
     * → 1 requête SQL COUNT() optimisée au lieu de N résultats chargés en mémoire
     *
     * PRINCIPE DDD : Le port reste le contrat UNIQUE.
     * Le DashboardService injecte TaskPersistencePort et appelle countAll(),
     * countByStatus(), etc. Il ne connaît ni SQL ni JPA.
     *
     * PRINCIPE ISP (Interface Segregation Principle) :
     * On ajoute au port existant plutôt que de créer un "DashboardPort" séparé,
     * car les données comptées sont des tâches — le port de tâches est le bon endroit.
     */

    /**
     * Compte toutes les tâches actives (deletedAt IS NULL).
     *
     * UTILISÉ PAR : DashboardService.getGlobalStats() / getUserStats()
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL
     */
    long countAll();

    /**
     * Compte les tâches actives ayant un statut donné.
     *
     * UTILISÉ PAR : DashboardService pour le pie chart par statut
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND status = :status
     *
     * @param status Le statut à filtrer (TODO, DOING, DONE)
     */
    long countByStatus(Task.TaskStatus status);

    /**
     * Compte les tâches actives par priorité.
     *
     * UTILISÉ PAR : DashboardService pour le bar chart par priorité
     *
     * RETOURNE UN MAP : { "LOW": 5, "MEDIUM": 12, "HIGH": 3, "CRITICAL": 1 }
     * → Chaque clé est le nom de l'enum (TaskPriority.name())
     * → Chaque valeur est le nombre de tâches actives avec cette priorité
     *
     * POURQUOI UN MAP ET PAS 4 MÉTHODES ?
     * → Un seul appel au port = une seule transaction
     * → L'adaptateur peut optimiser (1 requête avec GROUP BY, ou 4 requêtes parallèles)
     *
     * SQL POSSIBLE (optimisé) :
     * SELECT t.priority, COUNT(*) FROM tasks t
     * WHERE t.deleted_at IS NULL GROUP BY t.priority
     */
    Map<String, Long> countByPriority();

    /**
     * Compte les tâches en retard.
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < NOW() → la date d'échéance est dépassée
     * - status ≠ DONE → la tâche n'est pas encore terminée
     * - deletedAt IS NULL → la tâche est active (non archivée)
     *
     * UTILISÉ PAR : DashboardService pour la carte "Tâches en retard"
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL
     *               AND due_date < CURRENT_TIMESTAMP
     *               AND status <> 'DONE'
     */
    long countOverdue();

    /**
     * Compte les tâches actives créées par un utilisateur donné.
     *
     * UTILISÉ PAR : DashboardService.getUserStats(email)
     * pour le Dashboard USER (ne voit que ses propres stats)
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND user_id = :userId
     */
    long countByUserId(String userId);

    /**
     * Compte les tâches actives assignées à un utilisateur donné.
     *
     * UTILISÉ PAR : DashboardService.getUserStats(email)
     * pour le Dashboard USER (compte les tâches qu'on lui a assignées)
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND assignee_id = :assigneeId
     */
    long countByAssigneeId(String assigneeId);
}