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
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : TaskRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * INTERFACE DU PORT DE SORTIE : Ce repository implémente
 *间接ement le port TaskPersistencePort via TaskPersistenceAdapter.
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
}