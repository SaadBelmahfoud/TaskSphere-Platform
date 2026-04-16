package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/*
 * ====================================================================
 * REPOSITORY : TASK (Accès aux données)
 * ====================================================================
 *
 * PRINCIPE SPRING DATA JPA :
 * Tu déclares juste la signature de la méthode, et Spring génère le SQL.
 * - findByUserIdAndDeletedAtIsNull → SELECT * WHERE user_id = ? AND deleted_at IS NULL
 * - findByIdAndDeletedAtIsNullAndUserId → SELECT * WHERE id = ? AND deleted_at IS NULL AND user_id = ?
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    /** Trouver toutes les tâches actives d'un utilisateur (non supprimées) */
    Page<TaskEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Trouver une tâche par ID si elle n'est pas supprimée et appartient à un user */
    Optional<TaskEntity> findByIdAndDeletedAtIsNull(String id);

    /** Trouver une tâche active appartenant à un utilisateur spécifique */
    Optional<TaskEntity> findByIdAndDeletedAtIsNullAndUserId(String id, String userId);
}