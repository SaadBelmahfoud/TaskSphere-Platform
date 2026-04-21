package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}