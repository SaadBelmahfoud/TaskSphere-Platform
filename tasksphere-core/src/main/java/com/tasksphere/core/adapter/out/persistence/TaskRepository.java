package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/*
 * ====================================================================
 * REPOSITORY : TASK (Accès aux données via Spring Data JPA)
 * ====================================================================
 *
 * PRINCIPE SPRING DATA JPA :
 * Tu déclares juste la signature de la méthode, et Spring génère le SQL.
 * Pas besoin d'écrire de requêtes SQL !
 *
 * CONVENTION DE NOMMAGE → SQL :
 * - findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc
 *   → SELECT * FROM tasks WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC
 *
 * - findByIdAndDeletedAtIsNull
 *   → SELECT * FROM tasks WHERE id = ? AND deleted_at IS NULL
 *
 * - findByIdAndDeletedAtIsNullAndUserId
 *   → SELECT * FROM tasks WHERE id = ? AND deleted_at IS NULL AND user_id = ?
 *
 * PRINCIPE DU REPOSITORY PATTERN :
 * JpaRepository<TaskEntity, String> fournit déjà :
 * - save() : INSERT ou UPDATE
 * - findById() : SELECT par ID
 * - findAll() : SELECT *
 * - delete() : DELETE physique (à éviter, on utilise soft delete)
 * - count() : COUNT(*)
 *
 * On ajoute uniquement les méthodes spécifiques à notre domaine.
 *
 * PRINCIPE Page<TaskEntity> :
 * Retourne une page de résultats avec les métadonnées de pagination :
 * - getContent() : la liste des éléments
 * - getTotalElements() : nombre total d'éléments
 * - getTotalPages() : nombre total de pages
 * - getNumber() : numéro de la page courante
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    /** Trouver toutes les tâches actives d'un utilisateur, triées par date de création descendante */
    Page<TaskEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Trouver une tâche par ID si elle n'est pas supprimée (deletedAt IS NULL) */
    Optional<TaskEntity> findByIdAndDeletedAtIsNull(String id);

    /** Trouver une tâche active appartenant à un utilisateur spécifique */
    Optional<TaskEntity> findByIdAndDeletedAtIsNullAndUserId(String id, String userId);
}