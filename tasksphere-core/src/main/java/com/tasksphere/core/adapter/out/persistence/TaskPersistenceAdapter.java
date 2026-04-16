package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/*
 * ====================================================================
 * ADAPTATEUR DE PERSISTANCE (Pont Domaine ↔ JPA)
 * ====================================================================
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * L'adaptateur implémente le Port (interface du domaine) et traduit
 * les appels en opérations JPA concrètes.
 *
 * Le domaine n'a AUCUNE idée que JPA existe. Il ne voit que l'interface.
 * Si on change de BDD demain (PostgreSQL, MongoDB), seul cet adaptateur change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPersistenceAdapter implements TaskPersistencePort {

    private final TaskRepository taskRepository;

    @Override
    public Task save(Task task) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la tâche '{}' (id: {})", task.title(), task.id());
        TaskEntity entity = new TaskEntity(task);
        TaskEntity saved = taskRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Page<Task> findByUserId(String userId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {}", userId);
        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Optional<Task> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche de la tâche {}", id);
        return taskRepository.findByIdAndDeletedAtIsNull(id)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Optional<Task> findByIdAndUserId(String id, String userId) {
        log.debug("ADAPTATEUR JPA : Recherche tâche {} pour l'utilisateur {}", id, userId);
        return taskRepository.findByIdAndDeletedAtIsNullAndUserId(id, userId)
                .map(TaskEntity::toDomain);
    }

    @Override
    public void softDelete(String id) {
        log.debug("ADAPTATEUR JPA : Soft delete de la tâche {}", id);
        taskRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.deletedAt = java.time.LocalDateTime.now();
            taskRepository.save(entity);
        });
    }
}