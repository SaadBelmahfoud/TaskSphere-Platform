package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.port.out.UserInformationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE MÉTIER : TaskManager
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Cœur de la logique métier. Ce service implémente les
 * règles de gestion des tâches, y compris le RBAC et l'audit trail.
 *
 * ARCHITECTURE : Ce service est dans le DOMAINE (package service).
 * Il ne connaît ni HTTP (pas de @RestController), ni JPA (pas de @Entity).
 * Il ne travaille qu'avec des objets du domaine (Task) et des ports.
 *
 * @Transactional :
 * ─────────────────
 * Chaque méthode publique est transactionnelle par défaut.
 * - @Transactional (écriture) : ouvre une transaction, commit au retour
 * - @Transactional(readOnly = true) : optimisation pour la lecture
 *   → Hibernate peut désactiver le dirty checking (gain de perf)
 *
 * INJECTION DES DÉPENDANCES :
 * ──────────────────────────
 * 1. TaskPersistencePort : port de sauvegarde (interface)
 * 2. EventPublisherPort : port de publication d'événements (TaskCreatedEvent)
 * 3. UserInformationPort : port de récupération d'infos utilisateur (IAM)
 * 4. ActivityLogService : service d'audit log (Section 6)
 *
 * ═══════════════════════════════════════════════════════════════════
 * SECTION 6 — AUDIT TRAIL (Activity Log)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE : Audit comme Side Effect
 * ─────────────────────────────────
 * L'audit n'est PAS la responsabilité principale du TaskManager.
 * C'est un effet secondaire (side effect) de chaque opération d'écriture.
 *
 * On le capture APRÈS chaque opération réussie :
 * 1. L'opération métier s'exécute (création, modification, etc.)
 * 2. Si succès → on logge l'activité via ActivityLogService
 * 3. Si l'audit échoue → on loggue un WARN mais on NE fait PAS échouer l'opération
 *
 * POURQUOI ACTIVITYLOGSERVICE ET PAS ACTIVITYLOGPORT DIRECTEMENT ?
 * ──────────────────────────────────────────────────────────────
 * ActivityLogService est un service d'application (Application Service)
 * qui encapsule la logique de création d'ActivityLog :
 * - Il crée l'objet ActivityLog via la factory method ActivityLog.create()
 * - Il sauvegarde via ActivityLogPort
 *
 * En utilisant ActivityLogService, le TaskManager :
 * - Appelle activityLogService.log(action, description, username, taskId, taskTitle)
 * - Ne connaît PAS ActivityLog.create() ni les détails de construction
 * - Reste centré sur la logique tâche, pas sur le formatage d'audit
 *
 * OPÉRATIONS AUDITÉES :
 * ────────────────────
 * createTask()          → TASK_CREATED
 * updateTask()          → TASK_UPDATED
 * updateTaskStatus()    → TASK_STATUS_CHANGED (avec old → new)
 * assignTask()          → TASK_ASSIGNED ou TASK_UNASSIGNED
 * deleteTask()          → TASK_DELETED
 *
 * POURQUOI TRY-CATCH DANS logActivity() ?
 * ────────────────────────────────────
 * L'audit est un EFFET SECONDAIRE (side effect), pas l'opération principale.
 * Si l'audit échoue (DB indisponible, timeout, etc.) :
 * → On loggue un WARN (pour que l'admin sache qu'il y a un problème)
 * → On NE propage PAS l'exception
 * → L'opération métier (création, modification, etc.) est déjà réussie
 *
 * SANS CE TRY-CATCH :
 * - L'exception remonte dans le service
 * - @Transactional détecte l'exception RuntimeException
 * - La transaction est ROLLBACK → la tâche n'est PAS créée/modifiée
 * - → L'audit a empêché l'opération métier ! C'est inacceptable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final UserInformationPort userInformationPort;

    /**
     * Service d'audit trail (Section 6 — Collaboration).
     *
     * INJECTÉ PAR SPRING (constructor injection via @RequiredArgsConstructor).
     * ActivityLogService est un service d'application dans le package service.
     * Il encapsule la création et la sauvegarde des entrées d'audit.
     *
     * PRINCIPE DDD : Le service ne dépend que d'une abstraction
     * (le service ActivityLogService, pas le port directement).
     * Si le format d'audit change, seul ActivityLogService est modifié.
     *
     * CORRECTION (vs version précédente) :
     * AVANT : private final ActivityLogPersistencePort activityLogPort;
     *   → Erreur : ActivityLogPersistencePort n'existe pas.
     *   → Le port s'appelle ActivityLogPort.
     *
     * APRÈS : private final ActivityLogService activityLogService;
     *   → ✅ ActivityLogService existe dans le package service
     *   → Il wrappe ActivityLogPort et gère la création d'ActivityLog
     */
    private final ActivityLogService activityLogService;

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TÂCHE
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Task createTask(String title, String description, String currentUsername) {
        return createTask(title, description, currentUsername, null, null, null);
    }

    /**
     * Crée une tâche avec tous les paramètres optionnels.
     *
     * FLUX :
     * 1. Récupérer les infos utilisateur (email, rôle)
     * 2. Créer le Task via factory method
     * 3. Appliquer les modifications optionnelles (priority, dueDate, assigneeId)
     * 4. Sauvegarder via le port de persistance
     * 5. Publier un événement TaskCreatedEvent
     * 6. [Section 6] Logger l'activité TASK_CREATED
     */
    @Transactional
    public Task createTask(String title, String description, String currentUsername,
                           String priority, LocalDate dueDate, String assigneeId) {
        log.info("SERVICE : Création de la tâche '{}' par {}", title, currentUsername);

        var userInfo = userInformationPort.getUserInfo(currentUsername);

        Task taskToSave = Task.create(title, description != null ? description : "", currentUsername);

        if (priority != null && !priority.isBlank()) {
            taskToSave = taskToSave.updatePriority(Task.TaskPriority.valueOf(priority));
        }
        if (dueDate != null) {
            taskToSave = taskToSave.updateDueDate(dueDate);
        }
        if (assigneeId != null && !assigneeId.isBlank()) {
            taskToSave = taskToSave.assignTo(assigneeId);
        }

        Task savedTask = persistencePort.save(taskToSave);
        eventPublisher.publishTaskCreated(TaskCreatedEvent.of(savedTask.id(), savedTask.title()));

        // ═══════════════════════════════════════════════════════
        // [Section 6] AUDIT : Création de tâche
        // ═══════════════════════════════════════════════════════
        StringBuilder details = new StringBuilder("Tâche créée");
        if (savedTask.priority() != Task.TaskPriority.MEDIUM) {
            details.append(" avec priorité ").append(savedTask.priority().name());
        }
        if (savedTask.assigneeId() != null) {
            details.append(" assignée à ").append(savedTask.assigneeId());
        }
        logActivity(ActivityLog.Action.TASK_CREATED,
                currentUsername, details.toString(), savedTask.id(), savedTask.title());

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
        return persistencePort.findByUserId(currentUsername, pageable);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * RECHERCHE DYNAMIQUE AVEC FILTRES + RBAC
     * ═══════════════════════════════════════════════════════════
     *
     * LOGIQUE RBAC POUR LA RECHERCHE :
     * ──────────────────────────────────
     *
     * ADMIN ou MANAGER :
     * → Recherche GLOBALE (voient toutes les tâches de tous les utilisateurs)
     *
     * USER :
     * → Recherche LIMITÉE aux tâches créées + assignées
     * → Les résultats sont MERGÉS et dédupliqués
     */
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
            TaskPersistencePort.TaskSearchCriteria ownedCriteria =
                    new TaskPersistencePort.TaskSearchCriteria(
                            criteria.keyword(), currentUsername, null,
                            criteria.status(), criteria.priority(),
                            criteria.dueDateFrom(), criteria.dueDateTo(),
                            criteria.createdFrom(), criteria.createdTo()
                    );
            Page<Task> ownedTasks = persistencePort.searchTasks(ownedCriteria, pageable);

            TaskPersistencePort.TaskSearchCriteria assignedCriteria =
                    new TaskPersistencePort.TaskSearchCriteria(
                            criteria.keyword(), null, currentUsername,
                            criteria.status(), criteria.priority(),
                            criteria.dueDateFrom(), criteria.dueDateTo(),
                            criteria.createdFrom(), criteria.createdTo()
                    );
            Page<Task> assignedTasks = persistencePort.searchTasks(assignedCriteria, pageable);

            var mergedContent = Stream.concat(
                            ownedTasks.getContent().stream(),
                            assignedTasks.getContent().stream())
                    .distinct().toList();

            long totalElements = Math.max(ownedTasks.getTotalElements(),
                    assignedTasks.getTotalElements());
            return new PageImpl<>(mergedContent, pageable, totalElements);
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
        return persistencePort.findByIdAndUserId(taskId, currentUsername);
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
        } else {
            existingTask = persistencePort.findByIdAndUserId(taskId, currentUsername).orElse(null);
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

        // [Section 6] AUDIT
        logActivity(ActivityLog.Action.TASK_UPDATED,
                currentUsername, details.toString(), taskId, savedTask.title());

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

        // [Section 6] AUDIT : Transition old → new
        String details = String.format("Statut changé : %s → %s", oldStatus.name(), status.name());
        logActivity(ActivityLog.Action.TASK_STATUS_CHANGED,
                currentUsername, details, taskId, savedTask.title());

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

        String effectiveAssigneeId = (assigneeId != null && !assigneeId.isBlank())
                ? assigneeId : null;
        Task savedTask = persistencePort.save(existingTask.assignTo(effectiveAssigneeId));

        // [Section 6] AUDIT : TASK_ASSIGNED ou TASK_UNASSIGNED
        if (effectiveAssigneeId != null) {
            logActivity(ActivityLog.Action.TASK_ASSIGNED,
                    currentUsername, "Tâche assignée à " + effectiveAssigneeId,
                    taskId, savedTask.title());
        } else {
            logActivity(ActivityLog.Action.TASK_DELETED,
                    currentUsername, "Assignation retirée",
                    taskId, savedTask.title());
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
            logActivity(ActivityLog.Action.TASK_DELETED,
                    currentUsername, "Tâche supprimée (ADMIN)", taskId, null);
            return true;
        }
        if (persistencePort.findByIdAndUserId(taskId, currentUsername).isEmpty()) return false;
        persistencePort.softDelete(taskId);
        logActivity(ActivityLog.Action.TASK_DELETED,
                currentUsername, "Tâche supprimée par son créateur", taskId, null);
        return true;
    }

    // ═══════════════════════════════════════════════════════
    // [Section 6] AUDIT TRAIL — Méthode utilitaire privée
    // ═══════════════════════════════════════════════════════

    /**
     * Enregistre une action dans l'audit log (fail-safe).
     *
     * Délègue à ActivityLogService.log() qui :
     * 1. Crée un ActivityLog via la factory method
     * 2. Le sauvegarde via ActivityLogPort
     *
     * Le try-catch garantit que si l'audit échoue, l'opération
     * métier principale (déjà réussie) n'est pas impactée.
     *
     * @param action    Le type d'action (ActivityLog.Action)
     * @param actorEmail L'email de l'utilisateur qui fait l'action
     * @param details    Description humaine de l'action
     * @param taskId     ID de la tâche concernée (null si hors contexte)
     * @param taskTitle  Titre de la tâche (denormalized, null si hors contexte)
     */
    private void logActivity(ActivityLog.Action action, String actorEmail,
                             String details, String taskId, String taskTitle) {
        try {
            activityLogService.log(action, details, actorEmail, taskId, taskTitle);
        } catch (Exception e) {
            log.warn("AUDIT TRAIL : Échec de l'enregistrement — [action={}, actor={}] — Cause : {}",
                    action, actorEmail, e.getMessage());
        }
    }
}