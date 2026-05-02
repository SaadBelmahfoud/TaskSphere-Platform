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
 * PORT SORTANT : Persistance des tâches — CRUD + Recherche (Contrat du domaine)
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
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 3 : Split ISP — Retrait des méthodes de comptage
 * ═══════════════════════════════════════════════════════════════════
 *
 * MÉTHODES RETIRÉES (déplacées vers TaskDashboardPort) :
 * ──────────────────────────────────────────────────────
 * Les 6 méthodes RBAC-aware de comptage sont désormais dans
 * TaskDashboardPort. Ce port ne contient PLUS QUE les méthodes
 * CRUD et de recherche.
 *
 * MÉTHODES RETIRÉES :
 * - long countActiveTasks(String username)
 * - Map<String, Long> countByStatus(String username)
 * - Map<String, Long> countByPriority(String username)
 * - long countCreatedAfter(String username, LocalDateTime after)
 * - long countCompletedAfter(String username, LocalDateTime after)
 * - long countOverdueTasks(String username)
 *
 * MÉTHODES CONSERVÉES (non-RBAC, utilisées par TaskManager) :
 * - long countAll()
 * - long countByStatus(Task.TaskStatus status)
 * - Map<String, Long> countByPriority()
 * - long countOverdue()
 * - long countByUserId(String userId)
 * - long countByAssigneeId(String assigneeId)
 *
 * Pourquoi ces méthodes sont conservées ici ?
 * → Elles sont utilisées par TaskManager pour des vérifications internes
 *   (ex: nombre de tâches d'un utilisateur pour limiter les créations).
 * → Elles n'ont PAS le paramètre RBAC-aware (username nullable).
 * → Le DashboardService utilisera TaskDashboardPort pour les stats.
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

    /**
     * CORRECTION BUG 1 — Recherche par ID + (propriétaire OU assignataire)
     *
     * Cherche la tâche si l'utilisateur est créateur (userId) OU
     * assignataire (assigneeId). Cela permet à un USER de voir les
     * tâches qu'il a créées ET celles qu'on lui a assignées.
     *
     * @param id       L'ID de la tâche
     * @param username L'email de l'utilisateur (créateur ou assignataire)
     * @return La tâche si trouvée et l'utilisateur est impliqué
     */
    Optional<Task> findByIdAndUserIsOwnerOrAssignee(String id, String username);

    /**
     * CORRECTION BUG 1 — Liste des tâches d'un utilisateur (propriétaire OU assignataire)
     *
     * Retourne les tâches où l'utilisateur est créateur (userId)
     * OU assignataire (assigneeId), avec pagination.
     *
     * @param username L'email de l'utilisateur
     * @param pageable La pagination
     * @return Une page de tâches où l'utilisateur est impliqué
     */
    Page<Task> findByUserIsOwnerOrAssignee(String username, Pageable pageable);

    /** Supprimer logiquement une tâche (soft delete : set deletedAt = now) */
    void softDelete(String id);

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE (Sprint 2)
    // ═══════════════════════════════════════════════════════

    /**
     * Recherche dynamique avec filtres optionnels.
     *
     * PRINCIPE PARAMETER OBJECT :
     * Au lieu de passer 9 paramètres, on encapsule
     * tous les critères dans un record TaskSearchCriteria.
     *
     * @param criteria Les critères de recherche (tous optionnels)
     * @param pageable La pagination (page, size, sort)
     * @return Une page de tâches correspondant aux critères
     */
    Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable);

    /**
     * Recherche dynamique pour USER (propriétaire OU assignataire).
     *
     * UNE SEULE requête qui utilise (userId = :username OR assigneeId = :username)
     * dans la clause WHERE. La BDD gère le OR nativement, la pagination
     * est correcte, et il n'y a PAS de doublons.
     *
     * @param username L'email de l'utilisateur (toujours non-null pour USER)
     * @param criteria Les critères de recherche
     * @param pageable La pagination
     * @return Une page de tâches où l'utilisateur est impliqué
     */
    Page<Task> searchTasksForUser(String username, TaskSearchCriteria criteria, Pageable pageable);

    // ═══════════════════════════════════════════════════════
    // PARAMETER OBJECT : TaskSearchCriteria
    // ═══════════════════════════════════════════════════════

    /**
     * PARAMETER OBJECT : TaskSearchCriteria
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
    // MÉTHODES DE COMPTAGE NON-RBAC (conservées)
    // ═══════════════════════════════════════════════════════
    // Ces méthodes sont conservées ici car elles sont utilisées par
    // TaskManager pour des vérifications internes. Elles n'ont PAS
    // le paramètre RBAC-aware (username nullable).
    //
    // ═══════════════════════════════════════════════════════════════════
    // PHASE 2 — TÂCHE 3 : Méthodes RBAC-aware retirées
    // ═══════════════════════════════════════════════════════════════════
    // Les 6 méthodes RBAC-aware (countActiveTasks(String), countByStatus(String),
    // countByPriority(String), countCreatedAfter(String, LocalDateTime),
    // countCompletedAfter(String, LocalDateTime), countOverdueTasks(String))
    // ont été déplacées vers TaskDashboardPort.
    //
    // Pourquoi ces méthodes-ci restent ?
    // → countAll(), countByStatus(TaskStatus), countByPriority(), countOverdue(),
    //   countByUserId(String), countByAssigneeId(String) sont utilisées par
    //   TaskManager pour des vérifications internes (pas pour le Dashboard).
    // → Elles n'ont PAS le paramètre username nullable du pattern RBAC-aware.
    // ═══════════════════════════════════════════════════════════════════

    /** Compte toutes les tâches actives (deletedAt IS NULL). */
    long countAll();

    /** Compte les tâches actives ayant un statut donné. */
    long countByStatus(Task.TaskStatus status);

    /**
     * Compte les tâches actives par priorité.
     * RETOURNE UN MAP : { "LOW": 5, "MEDIUM": 12, "HIGH": 3, "CRITICAL": 1 }
     */
    Map<String, Long> countByPriority();

    /** Compte les tâches en retard (dueDate < now ET status ≠ DONE). */
    long countOverdue();

    /** Compte les tâches actives créées par un utilisateur donné. */
    long countByUserId(String userId);

    /** Compte les tâches actives assignées à un utilisateur donné. */
    long countByAssigneeId(String assigneeId);
}