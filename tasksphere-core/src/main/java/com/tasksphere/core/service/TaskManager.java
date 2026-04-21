package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.ActivityLogPersistencePort;
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
 * 2. EventPublisherPort : port de publication d'événements
 * 3. UserInformationPort : port de récupération d'infos utilisateur (IAM)
 * 4. ActivityLogPersistencePort : port de journal d'audit (Section 6)
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
 * 2. Si succès → on logge l'activité
 * 3. Si l'audit échoue → on loggue un WARN mais on NE fait PAS échouer l'opération
 *
 * POURQUOI CE CHOIX ARCHITECTURAL ?
 * ───────────────────────────────
 * 1. SÉPARATION DES RESPONSABILITÉS : Le TaskManager gère les tâches.
 *    L'audit est un cross-cutting concern (comme un aspect).
 * 2. RÉSILIENCE : Si le service d'audit est indisponible, les tâches
 *    continuent de fonctionner. L'audit est important mais pas critique.
 * 3. TRANSACTION : L'audit est dans la MÊME transaction que l'opération.
 *    Si la transaction rollback → l'audit est aussi annulé → cohérent.
 *
 * OPÉRATIONS AUDITÉES :
 * ────────────────────
 * createTask()          → TASK_CREATED
 * updateTask()          → TASK_UPDATED
 * updateTaskStatus()    → TASK_STATUS_CHANGED (avec old → new)
 * assignTask()          → TASK_ASSIGNED ou TASK_UNASSIGNED
 * deleteTask()          → TASK_DELETED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final UserInformationPort userInformationPort;

    /**
     * Port d'audit trail (Section 6 — Collaboration).
     *
     * INJECTÉ PAR SPRING (constructor injection via @RequiredArgsConstructor).
     * L'ActivityLogPersistencePort est une interface définie dans port/out/.
     * Son implémentation (ActivityLogPersistenceAdapter) est dans adapter/out/persistence/.
     *
     * PRINCIPE DDD : Le service ne dépend que d'une interface, jamais d'une implémentation.
     * Si l'audit change de backend (DB → Elasticsearch → Kafka), seul l'adapter change.
     */
    private final ActivityLogPersistencePort activityLogPort;

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
     *
     * @param title          Titre (obligatoire)
     * @param description   Description (optionnelle)
     * @param currentUsername Email de l'utilisateur courant (injecté par le contrôleur)
     * @param priority      Priorité (optionnelle)
     * @param dueDate       Date d'échéance (optionnelle)
     * @param assigneeId    Email de l'assignataire (optionnel, MANAGER/ADMIN uniquement)
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
        // On construit le détail de l'activité avec les informations
        // optionnelles (priorité, assignataire) si elles sont renseignées.
        StringBuilder details = new StringBuilder("Tâche créée");
        if (savedTask.priority() != Task.TaskPriority.MEDIUM) {
            details.append(" avec priorité ").append(savedTask.priority().name());
        }
        if (savedTask.assigneeId() != null) {
            details.append(" assignée à ").append(savedTask.assigneeId());
        }
        logActivity(savedTask.id(), ActivityLogPersistencePort.ActionType.TASK_CREATED,
                currentUsername, details.toString());

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
     * → Les critères userId/assigneeId sont passés tels quels
     *    (peuvent filtrer par créateur ou assignataire spécifique)
     *
     * USER :
     * → Recherche LIMITÉE aux tâches :
     *   1. Dont il est CRÉATEUR (userId = email courant)
     *   2. Dont il est ASSIGNATAIRE (assigneeId = email courant)
     * → Les résultats sont MERGÉS et dédupliqués
     *
     * POURQUOI CE DOUBLE CHEMIN POUR USER ?
     * → Un USER doit voir les tâches qu'il a créées ET celles
     *   qu'on lui a assignées. On fait 2 requêtes et on fusionne.
     *
     * NOTE : La pagination avec le merge est approximative.
     * Pour une solution production, on utiliserait une requête
     * native avec OR (userId = ? OR assigneeId = ?).
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
            // ADMIN/MANAGER : recherche globale sans filtre userId
            return persistencePort.searchTasks(criteria, pageable);
        } else {
            // USER : recherche limitée à ses tâches + tâches assignées
            // Requête 1 : tâches créées par l'utilisateur
            TaskPersistencePort.TaskSearchCriteria ownedCriteria =
                    new TaskPersistencePort.TaskSearchCriteria(
                            criteria.keyword(), currentUsername, null,
                            criteria.status(), criteria.priority(),
                            criteria.dueDateFrom(), criteria.dueDateTo(),
                            criteria.createdFrom(), criteria.createdTo()
                    );
            Page<Task> ownedTasks = persistencePort.searchTasks(ownedCriteria, pageable);

            // Requête 2 : tâches assignées à l'utilisateur
            TaskPersistencePort.TaskSearchCriteria assignedCriteria =
                    new TaskPersistencePort.TaskSearchCriteria(
                            criteria.keyword(), null, currentUsername,
                            criteria.status(), criteria.priority(),
                            criteria.dueDateFrom(), criteria.dueDateTo(),
                            criteria.createdFrom(), criteria.createdTo()
                    );
            Page<Task> assignedTasks = persistencePort.searchTasks(assignedCriteria, pageable);

            // Fusion et déduplication (par id)
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

    /**
     * Met à jour une tâche.
     *
     * [Section 6] Audit : Après la mise à jour réussie, on loggue
     * TASK_UPDATED avec les détails des champs modifiés.
     */
    @Transactional
    public Optional<Task> updateTask(String taskId, String currentUsername, String currentRole,
                                     String title, String description, String priority, LocalDate dueDate) {
        // ADMIN peut modifier n'importe quelle tâche
        // USER ne peut modifier que ses propres tâches
        Task existingTask;
        if ("ADMIN".equals(currentRole)) {
            existingTask = persistencePort.findById(taskId).orElse(null);
        } else {
            existingTask = persistencePort.findByIdAndUserId(taskId, currentUsername).orElse(null);
        }
        if (existingTask == null) return Optional.empty();

        // Appliquer les modifications (immutabilité : chaque appel retourne une nouvelle instance)
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

        // ═══════════════════════════════════════════════════════
        // [Section 6] AUDIT : Mise à jour de tâche
        // ═══════════════════════════════════════════════════════
        logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_UPDATED,
                currentUsername, details.toString());

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // CHANGEMENT DE STATUT AVEC RBAC
    // ═══════════════════════════════════════════════════════

    /**
     * Change le statut d'une tâche.
     *
     * PERMISSIONS :
     * - Le CRÉATEUR (userId) peut changer le statut
     * - L'ASSIGNATAIRE (assigneeId) peut changer le statut
     * - ADMIN peut changer le statut de n'importe quelle tâche
     * - MANAGER peut changer le statut de n'importe quelle tâche
     *
     * UTILISÉ PAR LE KANBAN FRONTEND :
     * Le composant KanbanPage appelle useUpdateTaskStatusMutation()
     * qui invoque PATCH /tasks/{id}/status.
     *
     * [Section 6] Audit : Loggue TASK_STATUS_CHANGED avec la transition old → new.
     */
    @Transactional
    public Optional<Task> updateTaskStatus(String taskId, String currentUsername,
                                           String currentRole, String newStatus) {
        Task existingTask = persistencePort.findById(taskId).orElse(null);
        if (existingTask == null) return Optional.empty();

        // Vérification RBAC : owner, assignee, admin ou manager
        boolean isOwner = currentUsername.equals(existingTask.userId());
        boolean isAssignee = currentUsername.equals(existingTask.assigneeId());
        boolean isAdmin = "ADMIN".equals(currentRole);
        boolean isManager = "MANAGER".equals(currentRole);

        if (!isOwner && !isAssignee && !isAdmin && !isManager) return Optional.empty();

        Task.TaskStatus oldStatus = existingTask.status();
        Task.TaskStatus status = Task.TaskStatus.valueOf(newStatus);
        Task savedTask = persistencePort.save(existingTask.updateStatus(status));

        // ═══════════════════════════════════════════════════════
        // [Section 6] AUDIT : Changement de statut
        // ═══════════════════════════════════════════════════════
        // On capture la transition old → new pour l'audit.
        // Exemple : "Statut changé : TODO → DOING"
        String details = String.format("Statut changé : %s → %s", oldStatus.name(), status.name());
        logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_STATUS_CHANGED,
                currentUsername, details);

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // ASSIGNATION DE TÂCHE (MANAGER/ADMIN uniquement)
    // ═══════════════════════════════════════════════════════

    /**
     * Assigne une tâche à un utilisateur.
     *
     * RBAC : Seuls ADMIN et MANAGER peuvent assigner.
     *
     * @param taskId      L'ID de la tâche
     * @param currentUsername Email de l'utilisateur qui fait l'assignation
     * @param currentRole     Rôle de l'utilisateur (ADMIN/MANAGER requis)
     * @param assigneeId  Email de la personne à assigner (null = désassigner)
     *
     * [Section 6] Audit : Loggue TASK_ASSIGNED ou TASK_UNASSIGNED.
     */
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

        // ═══════════════════════════════════════════════════════
        // [Section 6] AUDIT : Assignation ou désassignation
        // ═══════════════════════════════════════════════════════
        // On distingue deux cas d'audit :
        // 1. TASK_ASSIGNED : on assigne la tâche à quelqu'un
        // 2. TASK_UNASSIGNED : on retire l'assignation (assigneeId = null)
        if (effectiveAssigneeId != null) {
            logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_ASSIGNED,
                    currentUsername, "Tâche assignée à " + effectiveAssigneeId);
        } else {
            logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_UNASSIGNED,
                    currentUsername, "Assignation retirée");
        }

        return Optional.of(savedTask);
    }

    // ═══════════════════════════════════════════════════════
    // SOFT DELETE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    /**
     * Supprime logiquement une tâche.
     *
     * [Section 6] Audit : Loggue TASK_DELETED avant la suppression effective.
     */
    @Transactional
    public boolean deleteTask(String taskId, String currentUsername, String currentRole) {
        if ("ADMIN".equals(currentRole)) {
            if (persistencePort.findById(taskId).isEmpty()) return false;
            persistencePort.softDelete(taskId);
            // ═══════════════════════════════════════════════════════
            // [Section 6] AUDIT : Suppression par ADMIN
            // ═══════════════════════════════════════════════════════
            logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_DELETED,
                    currentUsername, "Tâche supprimée (ADMIN)");
            return true;
        }
        if (persistencePort.findByIdAndUserId(taskId, currentUsername).isEmpty()) return false;
        persistencePort.softDelete(taskId);
        // ═══════════════════════════════════════════════════════
        // [Section 6] AUDIT : Suppression par le propriétaire
        // ═══════════════════════════════════════════════════════
        logActivity(taskId, ActivityLogPersistencePort.ActionType.TASK_DELETED,
                currentUsername, "Tâche supprimée par son créateur");
        return true;
    }

    // ═══════════════════════════════════════════════════════
    // [Section 6] AUDIT TRAIL — Méthode utilitaire privée
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * LOG ACTIVITY — Section 6 : Collaboration (Audit Trail)
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : Fail-Safe Audit Logging
     * ────────────────────────────────────
     * Cette méthode est invoquée APRÈS chaque opération d'écriture réussie.
     * Elle capture l'action dans le journal d'activité.
     *
     * POURQUOI UN TRY-CATCH ICI ?
     * ──────────────────────────────
     * L'audit est un EFFET SECONDAIRE (side effect), pas l'opération principale.
     * Si l'audit échoue (DB indisponible, timeout, etc.) :
     * → On loggue un WARN (pour que l'admin sache qu'il y a un problème)
     * → On NE propage PAS l'exception
     * → L'opération métier (création, modification, etc.) est déjà réussie
     *
     * SANS CE TRY-CATCH :
     * - Si l'audit échoue → l'exception remonte dans le service
     * - Le @Transactional détecte l'exception RuntimeException
     * - La transaction est ROLLBACK → la tâche n'est PAS créée/modifiée
     * - → L'audit a empêché l'opération métier ! C'est inacceptable.
     *
     * AVEC CE TRY-CATCH :
     * - L'opération métier est terminée avec succès
     * - L'audit échoue → WARN loggé, pas de propagation
     * - → L'utilisateur voit son action réussie, l'admin voit le WARN dans les logs
     *
     * NOTE SUR LA TRANSACTION :
     * ──────────────────────────
     * L'audit est dans la MÊME transaction @Transactional que l'opération.
     * Si l'opération échoue (avant l'audit) → la transaction est rollback →
     * l'audit n'est jamais écrit → cohérent (pas d'audit pour une opération qui a échoué).
     *
     * @param taskId    L'ID de la tâche concernée
     * @param actionType Le type d'action (TASK_CREATED, TASK_UPDATED, etc.)
     * @param actorEmail L'email de l'utilisateur qui a fait l'action
     * @param details    Les détails de l'action (texte libre, humainement lisible)
     */
    private void logActivity(String taskId, ActivityLogPersistencePort.ActionType actionType,
                             String actorEmail, String details) {
        try {
            activityLogPort.save(
                    com.tasksphere.core.domain.ActivityLog.create(
                            taskId, actionType, actorEmail, details
                    )
            );
        } catch (Exception e) {
            // L'audit a échoué → on loggue un WARN mais on NE propage PAS l'exception.
            // L'opération métier principale est déjà réussie à ce stade.
            log.warn("AUDIT TRAIL : Échec de l'enregistrement de l'activité " +
                            "[taskId={}, action={}, actor={}] — Cause : {}",
                    taskId, actionType, actorEmail, e.getMessage());
        }
    }
}