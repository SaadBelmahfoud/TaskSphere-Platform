package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Opérations CRUD sur les tâches
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE ISP (Interface Segregation Principle) — Le "I" de SOLID :
 * ──────────────────────────────────────────────────────────────────
 * "Les clients ne doivent pas être forcés de dépendre d'interfaces
 *  qu'ils n'utilisent pas." — Robert C. Martin
 *
 * AVANT (TaskPersistencePort monolithique — 18 méthodes) :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  TaskManager injectait TaskPersistencePort (18 méthodes)         │
 * │  → N'utilisait QUE les 8 méthodes CRUD                          │
 * │  → Dépendait artificiellement des 10 méthodes de comptage       │
 * │  → Si une méthode de comptage changeait → recompilation inutile │
 * │                                                                  │
 * │  DashboardService injectait TaskPersistencePort (18 méthodes)    │
 * │  → N'utilisait QUE les méthodes de comptage                     │
 * │  → Dépendait artificiellement des 8 méthodes CRUD               │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * APRÈS (2 ports séparés — ISP appliqué) :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  TaskManager injecte TaskCrudPort (8 méthodes CRUD)              │
 * │  → Ne connaît QUE les opérations dont il a besoin               │
 * │  → Changement de comptage = ZÉRO impact sur TaskManager         │
 * │                                                                  │
 * │  DashboardService injecte TaskDashboardPort (méthodes stats)     │
 * │  → Ne connaît QUE les opérations de comptage                    │
 * │  → Changement CRUD = ZÉRO impact sur DashboardService           │
 * │                                                                  │
 * │  TaskPersistenceAdapter implémente LES DEUX ports               │
 * │  → Car les données viennent de la même table (tasks)            │
 * │  → Un seul adaptateur = une seule connexion DB                  │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * MÉTHODES CRUD (8) :
 * ───────────────────
 * 1. save()                          — Création ou mise à jour
 * 2. findByUserId()                  — Tâches d'un utilisateur (propriétaire)
 * 3. findById()                      — Recherche par ID
 * 4. findByIdAndUserId()             — Recherche par ID + propriétaire
 * 5. findByIdAndUserIsOwnerOrAssignee() — Bug 1 : propriétaire OU assignataire
 * 6. findByUserIsOwnerOrAssignee()   — Bug 1 : Liste owner + assignee
 * 7. softDelete()                    — Suppression logique
 * 8. searchTasks() + searchTasksForUser() + TaskSearchCriteria — Recherche dynamique
 *
 * UTILISÉ PAR : TaskManager (service métier principal des tâches)
 */
public interface TaskCrudPort {

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
     * @param id       L'ID de la tâche
     * @param username L'email de l'utilisateur (créateur ou assignataire)
     * @return La tâche si trouvée et l'utilisateur est impliqué
     */
    Optional<Task> findByIdAndUserIsOwnerOrAssignee(String id, String username);

    /**
     * CORRECTION BUG 1 — Liste des tâches d'un utilisateur (propriétaire OU assignataire)
     *
     * @param username L'email de l'utilisateur
     * @param pageable La pagination
     * @return Une page de tâches où l'utilisateur est impliqué
     */
    Page<Task> findByUserIsOwnerOrAssignee(String username, Pageable pageable);

    /** Supprimer logiquement une tâche (soft delete : set deletedAt = now) */
    void softDelete(String id);

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE
    // ═══════════════════════════════════════════════════════

    /**
     * Recherche dynamique avec filtres optionnels (Parameter Object Pattern).
     *
     * @param criteria Les critères de recherche (tous optionnels)
     * @param pageable La pagination (page, size, sort)
     * @return Une page de tâches correspondant aux critères
     */
    Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable);

    /**
     * CORRECTION BUG 1 — Recherche dynamique pour USER (propriétaire OU assignataire)
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
     * Regroupe 9 filtres optionnels en un seul record.
     * Cohérence : ce record n'a de sens QUE pour les ports de tâches.
     * Encapsulation : il n'est visible que via TaskCrudPort.TaskSearchCriteria.
     *
     * FILTRES DISPONIBLES (9 filtres + pagination) :
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
            java.time.LocalDate dueDateFrom,         // Date d'échéance minimum
            java.time.LocalDate dueDateTo,           // Date d'échéance maximum
            java.time.LocalDateTime createdFrom,     // Date de création minimum
            java.time.LocalDateTime createdTo        // Date de création maximum
    ) {}
}