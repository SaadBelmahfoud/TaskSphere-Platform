package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.port.out.UserInformationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE MÉTIER : TaskManager
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Cœur de la logique métier. Ce service implémente les
 * règles de gestion des tâches, y compris le RBAC et l'audit trail.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 4 : Audit via événement post-commit
 * ═══════════════════════════════════════════════════════════════════
 *
 * CHANGEMENT MAJEUR : L'audit n'est PLUS appelé directement.
 *
 * AVANT : ActivityLogService injecté + appel direct dans chaque méthode
 *   private final ActivityLogService activityLogService;
 *   ...
 *   activityLogService.log(TASK_CREATED, details, username, taskId, taskTitle);
 *   → Problème : l'audit est dans la MÊME transaction que l'opération.
 *   → Si l'audit échoue et lève une RuntimeException, la transaction
 *     est rollback → l'opération métier échoue aussi !
 *   → Le try-catch dans logActivity() masquait ce problème mais
 *     ne le résolvait pas (perte silencieuse de logs).
 *
 * APRÈS : Publication d'un événement TaskAuditEvent
 *   eventPublisher.publishAuditEvent(new TaskAuditEvent(...));
 *   → L'audit est exécuté APRÈS le commit par TaskAuditEventListener.
 *   → Si l'audit échoue, l'opération métier est DÉJÀ commitée.
 *   → Pas de try-catch nécessaire dans le service.
 *   → Fiabilité : l'opération métier ne peut JAMAIS être impactée par l'audit.
 *
 * ActivityLogService n'est PLUS injecté dans TaskManager !
 * (il l'était avant via le champ `activityLogService`).
 *
 * ═══════════════════════════════════════════════════════════════════
 * SECTION 6 — AUDIT TRAIL (Activity Log) — ANCIENNE VERSION
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE : Audit comme Side Effect
 * ─────────────────────────────────
 * L'audit n'est PAS la responsabilité principale du TaskManager.
 * C'est un effet secondaire (side effect) de chaque opération d'écriture.
 *
 * PHASE 2 — TÂCHE 4 : L'audit est désormais un événement asynchrone.
 * Le TaskManager publie un événement et ne se soucie PLUS de savoir
 * si l'audit réussit ou échoue. C'est le TaskAuditEventListener
 * qui gère l'enregistrement réel, dans une transaction indépendante.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final UserInformationPort userInformationPort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 4 : ActivityLogService retiré de l'injection
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : ActivityLogService était injecté directement
     *   private final ActivityLogService activityLogService;
     *   → Appelé dans chaque méthode d'écriture (createTask, updateTask, etc.)
     *   → Même transaction → risque de rollback si l'audit échoue
     *
     * APRÈS : ActivityLogService n'est PLUS injecté
     *   → L'audit est publié via eventPublisher.publishAuditEvent()
     *   → Le TaskAuditEventListener gère l'enregistrement post-commit
     *   → Transaction indépendante → fiabilité garantie
     * ═══════════════════════════════════════════════════════════════════
     */

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TÂCHE
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Task createTask(String title, String description, String currentUsername) {
        return createTask(title, description, currentUsername, null, null, null);
    }

    @Transactional
    public Task createTask(String title, String description, String currentUsername,
                           String priority, LocalDate dueDate, String assigneeId) {
        log.info("SERVICE : Création de la tâche '{}' par {}", title, currentUsername);

        Task taskToSave = Task.create(title, description != null ? description : "", currentUsername);

        // CORRECTION B1 : valueOf() protégé par toUpperCase() + try-catch
        if (priority != null && !priority.isBlank()) {
            try {
                taskToSave = taskToSave.updatePriority(
                        Task.TaskPriority.valueOf(priority.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("SERVICE : Priorité invalide '{}' ignorée, utilisation de MEDIUM", priority);
            }
        }
        if (dueDate != null) {
            taskToSave = taskToSave.updateDueDate(dueDate);
        }

        // CORRECTION B9 : Vérification RBAC pour l'assignation à la création
        if (assigneeId != null && !assigneeId.isBlank()) {
            var userInfo = userInformationPort.getUserInfo(currentUsername);
            String userRole = userInfo.userRole();
            String cleanRole = userRole.startsWith("ROLE_")
                    ? userRole.substring(5) : userRole;
            if (!"ADMIN".equals(cleanRole) && !"MANAGER".equals(cleanRole)) {
                log.warn("RBAC : User {} (rôle: {}) a tenté d'assigner une tâche sans permission",
                        currentUsername, cleanRole);
            } else {
                // CORRECTION UUID→EMAIL : Résoudre l'assigneeId en email
                String resolvedAssigneeId = userInformationPort.resolveAssigneeToEmail(assigneeId);
                taskToSave = taskToSave.assignTo(resolvedAssigneeId);
            }
        }

        Task savedTask = persistencePort.save(taskToSave);
        eventPublisher.publishTaskCreated(TaskCreatedEvent.of(savedTask.id(), savedTask.title()));

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 2 — TÂCHE 4 : Audit via événement (post-commit)
        // ═══════════════════════════════════════════════════════════════════
        // AVANT : logActivity(TASK_CREATED, currentUsername, details, savedTask.id(), savedTask.title());
        //   → Appel direct à ActivityLogService → même transaction → risque de rollback
        //
        // APRÈS : eventPublisher.publishAuditEvent(new TaskAuditEvent(...))
        //   → Publication d'un événement → TaskAuditEventListener l'enregistre
        //     APRÈS le commit de la transaction → fiabilité garantie
        // ═══════════════════════════════════════════════════════════════════
        StringBuilder details = new StringBuilder("Tâche créée");
        if (savedTask.priority() != Task.TaskPriority.MEDIUM) {
            details.append(" avec priorité ").append(savedTask.priority().name());
        }
        if (savedTask.assigneeId() != null) {
            details.append(" assignée à ").append(savedTask.assigneeId());
        }
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TASK_CREATED,
                details.toString(),
                currentUsername,
                savedTask.id(),
                savedTask.title()
        ));

        log.info("SERVICE : Tâche créée avec succès (id: {}, user: {}, priority: {}, assignee: {})",
                savedTask.id(), currentUsername, savedTask.priority(), savedTask.assigneeId());
        return savedTask;
    }

    // ═══════════════════════════════════════════════════════
    // RECHERCHE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<Task> getMyTasks(String currentUsername, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return persistencePort.findByUserIsOwnerOrAssignee(currentUsername, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> searchTasks(TaskPersistencePort.TaskSearchCriteria criteria,
                                  int page, int size, String sortBy, String sortDir,
                                  String currentUsername, String currentRole) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        if ("ADMIN".equals(currentRole) || "MANAGER".equals(currentRole)) {
            return persistencePort.searchTasks(criteria, pageable);
        } else {
            return persistencePort.searchTasksForUser(currentUsername, criteria, pageable);
        }
    }

    // ═══════════════════════════════════════════════════════
    // LECTURE PAR ID AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Optional<Task> getTaskById(String taskId, String currentUsername, String currentRole) {
        if ("ADMIN".equals(currentRole) || "MANAGER".equals(currentRole)) {
            return persistencePort.findById(taskId);
        }
        return persistencePort.findByIdAndUserIsOwnerOrAssignee(taskId, currentUsername);
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Optional<Task> updateTask(String taskId, String currentUsername, String currentRole,
                                     String title, String description, String priority, LocalDate dueDate) {
        Task existingTask;
        if ("ADMIN".equals(currentRole)) {
            existingTask = persistencePort.findById(taskId).orElse(null);
        } else if ("MANAGER".equals(currentRole)) {
            existingTask = persistencePort.findById(taskId).orElse(null);
        } else {
            existingTask = persistencePort.findByIdAndUserIsOwnerOrAssignee(taskId, currentUsername).orElse(null);
        }
        if (existingTask == null) return Optional.empty();

        Task updatedTask = existingTask;
        StringBuilder details = new StringBuilder("Tâche modifiée");
        if (title != null && !title.isBlank()) {
            updatedTask = updatedTask.update(title, updatedTask.description());
            details.append(" — titre changé");
        }
        if (description != null) {
            updatedTask = updatedTask.update(updatedTask.title(), description);
            details.append(" — description modifiée");
        }
        if (priority != null) {
            Task.TaskPriority newPriority = Task.TaskPriority.valueOf(priority);
            updatedTask = updatedTask.updatePriority(newPriority);
            details.append(" — priorité → ").append(newPriority.name());
        }
        if (dueDate != null) {
            updatedTask = updatedTask.updateDueDate(dueDate);
            details.append(" — date d'échéance → ").append(dueDate);
        }

        Task savedTask = persistencePort.save(updatedTask);

        // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TASK_UPDATED,
                details.toString(),
                currentUsername,
                taskId,
                savedTask.title()
        ));

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // CHANGEMENT DE STATUT AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Optional<Task> updateTaskStatus(String taskId, String currentUsername,
                                           String currentRole, String newStatus) {
        Task existingTask = persistencePort.findById(taskId).orElse(null);
        if (existingTask == null) return Optional.empty();

        boolean isOwner = currentUsername.equals(existingTask.userId());
        boolean isAssignee = currentUsername.equals(existingTask.assigneeId());
        boolean isAdmin = "ADMIN".equals(currentRole);
        boolean isManager = "MANAGER".equals(currentRole);

        if (!isOwner && !isAssignee && !isAdmin && !isManager) return Optional.empty();

        Task.TaskStatus oldStatus = existingTask.status();
        Task.TaskStatus status = Task.TaskStatus.valueOf(newStatus);
        Task savedTask = persistencePort.save(existingTask.updateStatus(status));

        // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
        String details = String.format("Statut changé : %s → %s", oldStatus.name(), status.name());
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TASK_STATUS_CHANGED,
                details,
                currentUsername,
                taskId,
                savedTask.title()
        ));

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // ASSIGNATION DE TÂCHE (MANAGER/ADMIN uniquement)
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Optional<Task> assignTask(String taskId, String currentUsername,
                                     String currentRole, String assigneeId) {
        if (!"ADMIN".equals(currentRole) && !"MANAGER".equals(currentRole))
            return Optional.empty();

        Task existingTask = persistencePort.findById(taskId).orElse(null);
        if (existingTask == null) return Optional.empty();

        // CORRECTION UUID→EMAIL : Résoudre l'assigneeId en email
        String effectiveAssigneeId = (assigneeId != null && !assigneeId.isBlank())
                ? userInformationPort.resolveAssigneeToEmail(assigneeId) : null;

        Task savedTask = persistencePort.save(existingTask.assignTo(effectiveAssigneeId));

        // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
        if (effectiveAssigneeId != null) {
            eventPublisher.publishAuditEvent(new TaskAuditEvent(
                    ActivityLog.Action.TASK_ASSIGNED,
                    "Tâche assignée à " + effectiveAssigneeId,
                    currentUsername,
                    taskId,
                    savedTask.title()
            ));
        } else {
            eventPublisher.publishAuditEvent(new TaskAuditEvent(
                    ActivityLog.Action.TASK_UNASSIGNED,
                    "Assignation retirée",
                    currentUsername,
                    taskId,
                    savedTask.title()
            ));
        }

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // SOFT DELETE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional
    public boolean deleteTask(String taskId, String currentUsername, String currentRole) {
        if ("ADMIN".equals(currentRole)) {
            if (persistencePort.findById(taskId).isEmpty()) return false;
            persistencePort.softDelete(taskId);
            // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
            eventPublisher.publishAuditEvent(new TaskAuditEvent(
                    ActivityLog.Action.TASK_DELETED,
                    "Tâche supprimée (ADMIN)",
                    currentUsername,
                    taskId,
                    null
            ));
            return true;
        }
        if (persistencePort.findByIdAndUserId(taskId, currentUsername).isEmpty()) return false;
        persistencePort.softDelete(taskId);
        // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TASK_DELETED,
                "Tâche supprimée par son créateur",
                currentUsername,
                taskId,
                null
        ));
        return true;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 4 : Méthode logActivity() RETIRÉE
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : Cette méthode privée encapsulait le try-catch autour
     * de activityLogService.log(). Elle masquait les erreurs d'audit.
     *
     * private void logActivity(ActivityLog.Action action, String actorEmail,
     *                          String details, String taskId, String taskTitle) {
     *     try {
     *         activityLogService.log(action, details, actorEmail, taskId, taskTitle);
     *     } catch (Exception e) {
     *         log.warn("AUDIT TRAIL : Échec de l'enregistrement — ...");
     *     }
     * }
     *
     * APRÈS : Cette méthode est SUPPRIMÉE. L'audit est publié via
     * eventPublisher.publishAuditEvent(new TaskAuditEvent(...)).
     * Le try-catch est maintenant dans TaskAuditEventListener.
     * ═══════════════════════════════════════════════════════════════════
     */
}