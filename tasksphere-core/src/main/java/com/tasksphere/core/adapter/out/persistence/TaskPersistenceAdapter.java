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
 * Schéma de flux :
 * Controller → Service (TaskManager) → Port (TaskPersistencePort) → Adaptateur (THIS)
 *                                                                       ↓
 *                                                                  Repository (JPA)
 *                                                                       ↓
 *                                                                    Base de données
 *
 * Le domaine n'a AUCUNE idée que JPA existe. Il ne voit que l'interface.
 * Si on change de BDD demain (PostgreSQL, MongoDB), seul cet adaptateur change.
 * Le service et le domaine restent identiques → zéro impact.
 *
 * PRINCIPE @Component :
 * Spring détecte automatiquement cette classe et la crée comme bean.
 * Spring l'injecte dans le TaskManager qui dépend de TaskPersistencePort.
 * C'est le mécanisme d'injection de dépendance (DI).
 *
 * PRINCIPE DE CONVERSION :
 * - Domaine → JPA : new TaskEntity(task) dans save()
 * - JPA → Domaine : entity.toDomain() dans les méthodes de lecture
 * Ces conversions sont MANUELLES et EXPLICITES pour garder le contrôle total.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPersistenceAdapter implements TaskPersistencePort {

    private final TaskRepository taskRepository;

    /**
     * Sauvegarder une tâche (création ou mise à jour).
     *
     * FLUX : Task (domaine) → TaskEntity (JPA) → BDD
     *
     * PRINCIPE repository.save() :
     * - Si isNew() = true → em.persist() → INSERT
     * - Si isNew() = false → em.merge() → UPDATE
     * C'est le flag @Transient isNew dans TaskEntity qui détermine le comportement.
     */
    @Override
    public Task save(Task task) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la tâche '{}' (id: {})", task.title(), task.id());
        TaskEntity entity = new TaskEntity(task);
        TaskEntity saved = taskRepository.save(entity);
        return saved.toDomain();
    }

    /**
     * Lister les tâches d'un utilisateur avec pagination.
     *
     * PRINCIPE .map(TaskEntity::toDomain) :
     * Page.map() transforme chaque élément de la page JPA en élément domaine.
     * C'est un mapping fonctionnel (Stream-like) spécifique à Spring Data.
     */
    @Override
    public Page<Task> findByUserId(String userId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {}", userId);
        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(TaskEntity::toDomain);
    }

    /**
     * Récupérer une tâche active par son ID.
     */
    @Override
    public Optional<Task> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche de la tâche {}", id);
        return taskRepository.findByIdAndDeletedAtIsNull(id)
                .map(TaskEntity::toDomain);
    }

    /**
     * Récupérer une tâche active appartenant à un utilisateur spécifique.
     */
    @Override
    public Optional<Task> findByIdAndUserId(String id, String userId) {
        log.debug("ADAPTATEUR JPA : Recherche tâche {} pour l'utilisateur {}", id, userId);
        return taskRepository.findByIdAndDeletedAtIsNullAndUserId(id, userId)
                .map(TaskEntity::toDomain);
    }

    /**
     * Soft delete : marque la tâche comme archivée en renseignant deletedAt.
     *
     * PRINCIPE .ifPresent() :
     * On ne fait le soft delete que si la tâche existe et est active.
     * Si la tâche n'existe pas ou est déjà supprimée, on ne fait rien.
     *
     * PRINCIPE DU SETTER :
     * On utilise entity.setDeletedAt() au lieu d'accéder directement au champ.
     * Le setter met aussi à jour updatedAt automatiquement (voir TaskEntity).
     */
    @Override
    public void softDelete(String id) {
        log.debug("ADAPTATEUR JPA : Soft delete de la tâche {}", id);
        taskRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.setDeletedAt(java.time.LocalDateTime.now());
            taskRepository.save(entity);
        });
    }
}