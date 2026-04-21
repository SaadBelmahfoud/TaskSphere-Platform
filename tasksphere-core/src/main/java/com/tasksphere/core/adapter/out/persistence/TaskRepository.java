package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task.TaskPriority;
import com.tasksphere.core.domain.Task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : TaskRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * INTERFACE DU PORT DE SORTIE : Ce repository implémente
 * indirectement le port TaskPersistencePort via TaskPersistenceAdapter.
 *
 * RAPPEL : Spring Data JPA génère automatiquement l'implémentation
 * de cette interface au démarrage. On n'écrit PAS de classe d'implémentation.
 * Spring crée un proxy dynamique basé sur les méthodes déclarées.
 *
 * MÉTHODES DERIVEES (Query Derivation) :
 * ───────────────────────────────────────
 * findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc
 * → Spring traduit automatiquement en JPQL :
 *   SELECT t FROM TaskEntity t
 *   WHERE t.userId = :userId AND t.deletedAt IS NULL
 *   ORDER BY t.createdAt DESC
 *
 * MÉTHODES @QUERY (JPQL explicite) :
 * ───────────────────────────────────
 * searchTasks() utilise un @Query avec des conditions
 * dynamiques (:param IS NULL OR ...) pour filtrer
 * uniquement les paramètres non-null.
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    /**
     * Récupère les tâches d'un utilisateur (soft delete exclu),
     * triées par date de création décroissante.
     *
     * Utilisée par : TaskPersistenceAdapter.findByUserId()
     */
    Page<TaskEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String userId, Pageable pageable);

    /**
     * Récupère une tâche par ID (soft delete exclu).
     * Utilisée par : RBAC ADMIN/MANAGER (voient toutes les tâches).
     */
    Optional<TaskEntity> findByIdAndDeletedAtIsNull(String id);

    /**
     * Récupère une tâche par ID + userId (soft delete exclu).
     * Utilisée par : RBAC USER (ne voit que ses propres tâches).
     */
    Optional<TaskEntity> findByIdAndDeletedAtIsNullAndUserId(String id, String userId);

    /**
     * ═══════════════════════════════════════════════════════════
     * RECHERCHE DYNAMIQUE AVEC FILTRES OPTIONNELS
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : ":param IS NULL OR condition"
     * ───────────────────────────────────────
     * Quand un paramètre est null, la condition "IS NULL" est vraie
     * → le filtre est ignoré.
     * Quand un paramètre est non-null, la condition après OR est évaluée.
     *
     * EXEMPLE avec keyword = "urgence" et status = null :
     * WHERE ... AND (NULL IS NULL                            → FALSE
     *                OR LOWER(title) LIKE '%urgence%'        → évalué
     *                OR LOWER(description) LIKE '%urgence%') → évalué)
     *         AND (NULL IS NULL                    → TRUE
     *                OR t.status = NULL)            → ignoré
     *
     * → Résultat : filtre sur keyword uniquement !
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
     * PAGINATION : Spring Data Pageable gère automatiquement
     * le LIMIT/OFFSET via page et size.
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.deletedAt IS NULL " +
            "AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:userId IS NULL OR t.userId = :userId) " +
            "AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:dueDateFrom IS NULL OR t.dueDate >= :dueDateFrom) " +
            "AND (:dueDateTo IS NULL OR t.dueDate <= :dueDateTo) " +
            "AND (:createdFrom IS NULL OR t.createdAt >= :createdFrom) " +
            "AND (:createdTo IS NULL OR t.createdAt <= :createdTo)")
    Page<TaskEntity> searchTasks(
            @Param("keyword") String keyword,
            @Param("userId") String userId,
            @Param("assigneeId") String assigneeId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            Pageable pageable
    );

    // ═══════════════════════════════════════════════════════
    // MÉTHODES DE COMPTAGE POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * COMPTAGE POUR DASHBOARD — Section 6 : Collaboration
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : Query Derivation de Spring Data JPA
     * ─────────────────────────────────────────────────
     * Spring Data traduit le nom de la méthode en JPQL/SQL automatiquement.
     *
     * countByDeletedAtIsNull
     * → SELECT COUNT(t) FROM TaskEntity t WHERE t.deletedAt IS NULL
     *
     * countByStatusAndDeletedAtIsNull
     * → SELECT COUNT(t) FROM TaskEntity t
     *   WHERE t.status = :status AND t.deletedAt IS NULL
     *
     * AVANTAGE : Pas besoin d'écrire de @Query pour des COUNT simples.
     * Spring Data s'en occupe à la compilation via le proxy dynamique.
     *
     * POURQUOI DES MÉTHODES SÉPARÉES ET PAS UN @QUERY GÉNÉRIQUE ?
     * → Chaque méthode a un nom explicite → auto-documentation
     * → Spring Data peut optimiser le COUNT (pas de création d'entité, juste un long)
     * → Testabilité : on peut vérifier chaque comptage indépendamment
     */

    /**
     * Compte toutes les tâches actives.
     * SQL : SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     */
    long countByDeletedAtIsNull();

    /**
     * Compte les tâches actives par statut.
     * SQL : SELECT COUNT(t) FROM tasks t WHERE t.status = ? AND t.deleted_at IS NULL
     */
    long countByStatusAndDeletedAtIsNull(TaskStatus status);

    /**
     * Compte les tâches actives créées par un utilisateur.
     * SQL : SELECT COUNT(t) FROM tasks t WHERE t.user_id = ? AND t.deleted_at IS NULL
     */
    long countByUserIdAndDeletedAtIsNull(String userId);

    /**
     * Compte les tâches actives assignées à un utilisateur.
     * SQL : SELECT COUNT(t) FROM tasks t WHERE t.assignee_id = ? AND t.deleted_at IS NULL
     *
     * NOTE : Spring Data gère correctement les valeurs NULL pour assigneeId.
     * Si assigneeId = null, la requête devient :
     * WHERE assignee_id IS NULL AND deleted_at IS NULL
     * → Compte les tâches NON assignées (ce qui n'est pas notre cas d'usage,
     *   car on passe toujours un email non-null).
     */
    long countByAssigneeIdAndDeletedAtIsNull(String assigneeId);

    /**
     * ═══════════════════════════════════════════════════════════
     * COMPTAGE PAR PRIORITÉ — GROUP BY (requête @Query explicite)
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : Cette méthode retourne une liste de Object[] (projection JPQL).
     * ────────────────────────────────────────────────────────────
     * Chaque Object[] contient 2 éléments :
     * - [0] = la priorité (TaskPriority enum)
     * - [1] = le nombre de tâches (Long)
     *
     * POURQUOI UN @QUERY ET PAS UNE MÉTHODE DÉRIVÉE ?
     * → Spring Data ne supporte pas le GROUP BY en query derivation.
     *   On ne peut PAS écrire "countGroupByPriorityAndDeletedAtIsNull".
     *   Il faut un @Query explicite avec GROUP BY.
     *
     * COMMENT UTILISER LE RÉSULTAT DANS L'ADAPTATEUR ?
     * ─────────────────────────────────────────────────
     * for (Object[] row : repository.countGroupByPriority()) {
     *     TaskPriority priority = (TaskPriority) row[0];
     *     Long count = (Long) row[1];
     *     map.put(priority.name(), count);
     * }
     *
     * POURQUOI ON NE RETOURNE PAS UN MAP DÉJÀ CONSTRUIT ?
     * → Le Repository JPA travaille avec des entités/projections.
     *   La transformation en Map<String, Long> est de la logique
     *   d'adaptation → c'est le rôle de TaskPersistenceAdapter.
     */
    @Query("SELECT t.priority, COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "GROUP BY t.priority")
    List<Object[]> countGroupByPriority();

    /**
     * ═══════════════════════════════════════════════════════════
     * COMPTAGE DES TÂCHES EN RETARD
     * ═══════════════════════════════════════════════════════════
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < NOW() → la date d'échéance est dépassée
     * - status ≠ DONE → la tâche n'est pas encore terminée
     * - deletedAt IS NULL → la tâche est active (non archivée)
     *
     * POURQUOI UN @QUERY ET PAS UNE MÉTHODE DÉRIVÉE ?
     * → On compare dueDate avec la fonction CURRENT_TIMESTAMP (now).
     *   Spring Data ne supporte pas les expressions temporelles
     *   dans les noms de méthodes dérivées.
     *
     * NOTE JPQL : CURRENT_TIMESTAMP = la date/heure actuelle du serveur BDD.
     * Pour H2 (dev) et PostgreSQL (prod), c'est supporté nativement.
     */
    @Query("SELECT COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND t.dueDate < CURRENT_TIMESTAMP " +
            "AND t.status <> 'DONE'")
    long countOverdue();

    // ═══════════════════════════════════════════════════════
    // MÉTHODES RBAC-AWARE POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * REQUÊTES @QUERY RBAC-AWARE — Filtre utilisateur optionnel
     * ═══════════════════════════════════════════════════════════════════
     *
     * NOUVEAU CONCEPT — ":param IS NULL OR" POUR LE RBAC :
     * ──────────────────────────────────────────────────
     * Toutes les méthodes ci-dessous utilisent le pattern :
     *   AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username)
     *
     * CE PATTERN PERMET :
     * ┌──────────────────────────────────────────────────────────────────┐
     * │  Si username = null (ADMIN/MANAGER) :                          │
     * │  → "NULL IS NULL" = TRUE → le filtre est court-circuité        │
     * │  → On compte TOUTES les tâches (vue globale)                   │
     * │                                                                  │
     * │  Si username = "email" (USER) :                                 │
     * │  → "NULL IS NULL" = FALSE → on évalue le OR                    │
     * │  → On ne compte que les tâches où email est créateur            │
     * │    OU assignataire                                              │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * AVANTAGE vs DEUX REQUÊTES SÉPARÉES :
     * - 1 seule méthode = 1 seul endroit à maintenir
     * - Le filtre RBAC est géré au niveau SQL (pas en Java)
     * - La BDD optimise mieux qu'une itération en mémoire
     *
     * POURQUOI ON N'UTILISE PAS COUNT(DISTINCT t) ?
     * → On filtre sur une SEULE table (TaskEntity) avec des OR.
     *   Chaque ligne de tâche ne peut apparaître qu'une seule fois
     *   dans le résultat → pas de risque de doublons → COUNT(t) suffit.
     *
     * PERFORMANCE :
     * ┌──────────────────────────────────────────────────────────────┐
     * │  Chaque méthode exécute UNE SEULE requête SQL.               │
     * │  Pas de N+1, pas de chargement d'entités en mémoire.         │
     * │  Le COUNT est calculé par la BDD → performances optimales.   │
     * └──────────────────────────────────────────────────────────────┘
     */

    /**
     * Compte les tâches actives avec filtre RBAC optionnel.
     *
     * SQL (username = null) :
     * SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     *
     * SQL (username = "email") :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (t.user_id = 'email' OR t.assignee_id = 'email')
     */
    @Query("SELECT COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username)")
    long countActiveTasks(@Param("username") String username);

    /**
     * Compte les tâches actives par statut avec filtre RBAC optionnel.
     *
     * RETOURNE : List<Object[]> où chaque Object[] contient :
     * - [0] = TaskStatus (enum) : TODO, DOING, ou DONE
     * - [1] = Long : le nombre de tâches avec ce statut
     *
     * SQL :
     * SELECT t.status, COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     * GROUP BY t.status
     *
     * TRANSFORMATION EN MAP (dans l'adaptateur) :
     * for (Object[] row : result) {
     *     map.put(((TaskStatus) row[0]).name(), (Long) row[1]);
     * }
     * → { "TODO": 10, "DOING": 8, "DONE": 7 }
     */
    @Query("SELECT t.status, COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username) " +
            "GROUP BY t.status")
    List<Object[]> countGroupByStatus(@Param("username") String username);

    /**
     * Compte les tâches actives par priorité avec filtre RBAC optionnel.
     *
     * RETOURNE : List<Object[]> où chaque Object[] contient :
     * - [0] = TaskPriority (enum) : LOW, MEDIUM, HIGH, CRITICAL
     * - [1] = Long : le nombre de tâches avec cette priorité
     *
     * SQL :
     * SELECT t.priority, COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     * GROUP BY t.priority
     */
    @Query("SELECT t.priority, COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username) " +
            "GROUP BY t.priority")
    List<Object[]> countGroupByPriorityFiltered(@Param("username") String username);

    /**
     * Compte les tâches actives créées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Créées cette semaine"
     *
     * SQL :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND t.createdAt >= :after
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     *
     * NOTE : completedAt est null pour les tâches non terminées.
     * On filtre sur createdAt car on veut les tâches CRÉÉES, pas terminées.
     */
    @Query("SELECT COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND t.createdAt >= :after " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username)")
    long countCreatedAfter(@Param("username") String username, @Param("after") LocalDateTime after);

    /**
     * Compte les tâches actives complétées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Terminées cette semaine"
     *
     * SQL :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND t.completedAt >= :after
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     *
     * NOTE : completedAt est non-null UNIQUEMENT quand status = DONE.
     * Donc cette méthode compte implicitement les tâches terminées.
     */
    @Query("SELECT COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND t.completedAt >= :after " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username)")
    long countCompletedAfter(@Param("username") String username, @Param("after") LocalDateTime after);

    /**
     * Compte les tâches en retard avec filtre RBAC optionnel.
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < CURRENT_TIMESTAMP → date d'échéance dépassée
     * - status <> 'DONE' → pas encore terminée
     * - deletedAt IS NULL → active (non archivée)
     *
     * SQL :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND t.due_date < CURRENT_TIMESTAMP
     *   AND t.status <> 'DONE'
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     *
     * NOTE JPQL : CURRENT_TIMESTAMP est supporté par H2 (dev) et PostgreSQL (prod).
     */
    @Query("SELECT COUNT(t) FROM TaskEntity t " +
            "WHERE t.deletedAt IS NULL " +
            "AND t.dueDate < CURRENT_TIMESTAMP " +
            "AND t.status <> 'DONE' " +
            "AND (:username IS NULL OR t.userId = :username OR t.assigneeId = :username)")
    long countOverdueTasks(@Param("username") String username);
}