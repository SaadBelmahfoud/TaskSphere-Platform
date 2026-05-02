package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskDashboardPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 3 : Implémentation des DEUX ports (ISP)
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT : TaskPersistenceAdapter implements TaskPersistencePort
 * → UNE interface avec 20+ méthodes
 *
 * APRÈS : TaskPersistenceAdapter
 *         implements TaskPersistencePort, TaskDashboardPort
 * → DEUX interfaces séparées (ISP)
 * → MÊME implémentation physique
 *
 * POURQUOI C'EST VALIDE ?
 * - Java permet d'implémenter plusieurs interfaces
 * - L'adaptateur a UNE implémentation mais DEUX contrats
 * - Spring injecte le bon port selon la dépendance :
 *   - TaskManager → TaskPersistencePort (CRUD)
 *   - DashboardService → TaskDashboardPort (comptage)
 * - Il n'y a QU'UN SEUL bean TaskPersistenceAdapter en mémoire
 *
 * SCHÉMA D'INJECTION SPRING :
 * ┌─────────────────────────────────────────────────────┐
 * │  TaskPersistenceAdapter (UN bean Spring)            │
 * │    implements TaskPersistencePort                    │
 * │    implements TaskDashboardPort                      │
 * │                                                      │
 * │  TaskManager injecte TaskPersistencePort ──────┐     │
 * │  DashboardService injecte TaskDashboardPort ──┐│     │
 * │                                                ││     │
 * │                   MÊME BEAN ───────────────────┘│     │
 * └─────────────────────────────────────────────────┘     │
 *                                                          │
 * Spring détecte que le bean TaskPersistenceAdapter         │
 * implémente LES DEUX interfaces et l'injecte               │
 * pour les deux dépendances.                                │
 * └──────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPersistenceAdapter implements TaskPersistencePort, TaskDashboardPort {

    private final TaskRepository taskRepository;

    // ═══════════════════════════════════════════════════════
    // MÉTHODES CRUD + RECHERCHE (TaskPersistencePort)
    // ═══════════════════════════════════════════════════════

    /**
     * Sauvegarder une tâche (création ou mise à jour).
     *
     * FLUX : Task (domaine) → TaskEntity (JPA) → BDD
     *
     * CHEMIN DUAL (INSERT vs UPDATE) :
     * - Si l'entité existe → récupérer l'entité MANAGÉE → setters → dirty checking → UPDATE
     * - Si nouvelle → new TaskEntity(task) avec isNew=true → INSERT
     *
     * PRINCIPE DU DIRTY CHECKING :
     * Hibernate compare l'état actuel de l'entité managée avec son snapshot.
     * Si des champs ont changé → UPDATE SQL automatique au commit.
     */
    @Override
    public Task save(Task task) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la tâche '{}' (id: {})", task.title(), task.id());

        Optional<TaskEntity> existingEntity = taskRepository.findByIdAndDeletedAtIsNull(task.id());

        if (existingEntity.isPresent()) {
            // CHEMIN UPDATE : L'entité existe déjà en BDD
            TaskEntity managedEntity = existingEntity.get();
            managedEntity.setTitle(task.title());
            managedEntity.setDescription(task.description());
            managedEntity.setStatus(task.status());
            managedEntity.setPriority(task.priority());
            managedEntity.setDueDate(task.dueDate());
            managedEntity.setCompletedAt(task.completedAt());
            managedEntity.setAssigneeId(task.assigneeId());

            log.debug("ADAPTATEUR JPA : Mise à jour de la tâche existante '{}'", task.title());
            return managedEntity.toDomain();

        } else {
            // CHEMIN INSERT : Nouvelle tâche, pas encore en BDD
            TaskEntity entity = new TaskEntity(task);
            TaskEntity saved = taskRepository.save(entity);
            log.debug("ADAPTATEUR JPA : Nouvelle tâche créée '{}'", task.title());
            return saved.toDomain();
        }
    }

    @Override
    public Page<Task> findByUserId(String userId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {}", userId);
        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Optional<Task> findByIdAndUserIsOwnerOrAssignee(String id, String username) {
        log.debug("ADAPTATEUR JPA : Recherche tâche {} pour utilisateur {} (owner OR assignee)", id, username);
        return taskRepository.findByIdAndUserIsOwnerOrAssignee(id, username)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Page<Task> findByUserIsOwnerOrAssignee(String username, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {} (owner OR assignee)", username);
        return taskRepository.findByUserIsOwnerOrAssignee(username, username, pageable)
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
            entity.setDeletedAt(java.time.LocalDateTime.now());
            taskRepository.save(entity);
        });
    }

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE AVEC FILTRES (TaskPersistencePort)
    // ═══════════════════════════════════════════════════════

    @Override
    public Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche dynamique avec critères keyword={}, userId={}, assigneeId={}, status={}, priority={}",
                criteria.keyword(), criteria.userId(), criteria.assigneeId(),
                criteria.status(), criteria.priority());

        Page<TaskEntity> result = taskRepository.searchTasks(
                criteria.keyword(), criteria.userId(), criteria.assigneeId(),
                criteria.status(), criteria.priority(),
                criteria.dueDateFrom(), criteria.dueDateTo(),
                criteria.createdFrom(), criteria.createdTo(), pageable
        );

        return result.map(TaskEntity::toDomain);
    }

    @Override
    public Page<Task> searchTasksForUser(String username, TaskSearchCriteria criteria, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche dynamique USER {} (owner OR assignee) keyword={}, status={}, priority={}",
                username, criteria.keyword(), criteria.status(), criteria.priority());

        Page<TaskEntity> result = taskRepository.searchTasksForUser(
                username, criteria.keyword(), criteria.status(), criteria.priority(),
                criteria.dueDateFrom(), criteria.dueDateTo(),
                criteria.createdFrom(), criteria.createdTo(), pageable
        );

        return result.map(TaskEntity::toDomain);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES DE COMPTAGE NON-RBAC (TaskPersistencePort)
    // ═══════════════════════════════════════════════════════
    // Ces méthodes sont conservées dans TaskPersistencePort car elles
    // sont utilisées par TaskManager pour des vérifications internes.

    @Override
    public long countAll() {
        log.debug("ADAPTATEUR JPA : Comptage de toutes les tâches actives");
        return taskRepository.countByDeletedAtIsNull();
    }

    @Override
    public long countByStatus(Task.TaskStatus status) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches avec statut {}", status);
        return taskRepository.countByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public Map<String, Long> countByPriority() {
        log.debug("ADAPTATEUR JPA : Comptage des tâches par priorité");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByPriority()) {
            Task.TaskPriority priority = (Task.TaskPriority) row[0];
            Long count = (Long) row[1];
            result.put(priority.name(), count);
        }
        return result;
    }

    @Override
    public long countOverdue() {
        log.debug("ADAPTATEUR JPA : Comptage des tâches en retard");
        return taskRepository.countOverdue();
    }

    @Override
    public long countByUserId(String userId) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches de l'utilisateur {}", userId);
        return taskRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public long countByAssigneeId(String assigneeId) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches assignées à {}", assigneeId);
        return taskRepository.countByAssigneeIdAndDeletedAtIsNull(assigneeId);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES RBAC-AWARE POUR LE DASHBOARD (TaskDashboardPort)
    // ═══════════════════════════════════════════════════════
    //
    // ═══════════════════════════════════════════════════════════════════
    // PHASE 2 — TÂCHE 3 : Ces méthodes implémentent TaskDashboardPort
    // ═══════════════════════════════════════════════════════════════════
    // AVANT : Ces méthodes étaient dans TaskPersistencePort (interface unique).
    // APRÈS : Ces méthodes sont dans TaskDashboardPort (interface séparée).
    // L'implémentation physique est IDENTIQUE — seul le contrat change.
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public long countActiveTasks(String username) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches actives (RBAC username={})",
                username != null ? username : "GLOBAL");
        return taskRepository.countActiveTasks(username);
    }

    @Override
    public Map<String, Long> countByStatus(String username) {
        log.debug("ADAPTATEUR JPA : Comptage par statut (RBAC username={})",
                username != null ? username : "GLOBAL");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByStatus(username)) {
            Task.TaskStatus status = (Task.TaskStatus) row[0];
            Long count = (Long) row[1];
            result.put(status.name(), count);
        }
        return result;
    }

    @Override
    public Map<String, Long> countByPriority(String username) {
        log.debug("ADAPTATEUR JPA : Comptage par priorité (RBAC username={})",
                username != null ? username : "GLOBAL");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByPriorityFiltered(username)) {
            Task.TaskPriority priority = (Task.TaskPriority) row[0];
            Long count = (Long) row[1];
            result.put(priority.name(), count);
        }
        return result;
    }

    @Override
    public long countCreatedAfter(String username, LocalDateTime after) {
        log.debug("ADAPTATEUR JPA : Comptage tâches créées après {} (RBAC username={})",
                after, username != null ? username : "GLOBAL");
        return taskRepository.countCreatedAfter(username, after);
    }

    @Override
    public long countCompletedAfter(String username, LocalDateTime after) {
        log.debug("ADAPTATEUR JPA : Comptage tâches complétées après {} (RBAC username={})",
                after, username != null ? username : "GLOBAL");
        return taskRepository.countCompletedAfter(username, after);
    }

    @Override
    public long countOverdueTasks(String username) {
        log.debug("ADAPTATEUR JPA : Comptage tâches en retard (RBAC username={})",
                username != null ? username : "GLOBAL");
        return taskRepository.countOverdueTasks(username);
    }
}