package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.TaskChangeLog;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TagPort;
import com.tasksphere.core.port.out.TaskChangeLogPort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE MÉTIER : TaskManager
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Cœur de la logique métier. Ce service implémente les
 * règles de gestion des tâches, y compris le RBAC, l'audit trail
 * et l'historique détaillé des changements.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — FEATURE 2 : Enregistrement des changements champ par champ
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — CHANGE LOGGING :
 * ────────────────────────────────────
 * Avant cette Phase 3, l'audit enregistrait uniquement l'ACTION globale
 * ("Tâche modifiée"). Maintenant, on enregistre AUSSI chaque champ
 * modifié avec son ancienne et nouvelle valeur.
 *
 * EXEMPLE :
 * L'utilisateur modifie une tâche (titre + priorité) →
 *   ActivityLog   : "Tâche modifiée — titre changé, priorité → HIGH"
 *   TaskChangeLog : 2 entrées :
 *     1. field_name=title, old="Ancien titre", new="Nouveau titre"
 *     2. field_name=priority, old="MEDIUM", new="HIGH"
 *
 * POURQUOI ENREGISTRER DANS LA MÊME TRANSACTION ?
 * ────────────────────────────────────────────────
 * Contrairement à ActivityLog (post-commit via événement),
 * les TaskChangeLogs sont enregistrés DANS la transaction métier.
 * Pourquoi ? Parce que :
 * 1. Les changements font partie INTÉGRANTE de l'opération
 * 2. Si la transaction rollback, les changements doivent aussi disparaître
 * 3. Pas de risque de "changement fantôme" sans opération
 *
 * PATTERN UTILISÉ : "Collecting Parameter"
 * ────────────────────────────────────────────
 * On accumule les changements dans une List<TaskChangeLog>
 * pendant la comparaison, puis on les sauvegarde en batch.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — FEATURE 3 : Gestion des tags lors de la création/modification
 * ═══════════════════════════════════════════════════════════════════
 * Le TaskManager gère maintenant l'association des tags aux tâches
 * lors de la création et de la modification. Il délègue la persistance
 * des associations (table task_tags) au TagPort.
 *
 * PRINCIPE — SYNCHRONISATION DES TAGS (modification) :
 * ─────────────────────────────────────────────────────
 * Lors d'une modification, si tagIds est fourni (non null), on
 * synchronise les tags : on ajoute les nouveaux et on retire les
 * anciens. Ce n'est PAS un ajout incrémental, c'est un REMPLACEMENT
 * complet de la liste. Cela correspond au comportement REST standard.
 *
 * Si tagIds est null → les tags ne sont PAS modifiés (PATCH sémantique).
 * Si tagIds est une liste vide → tous les tags sont retirés.
 * ═══════════════════════════════════════════════════════════════════
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
     * PHASE 3 — FEATURE 2 : Injection du TaskChangeLogPort
     * ═══════════════════════════════════════════════════════════════════
     * Permet d'enregistrer les changements champ par champ
     * dans la même transaction que l'opération métier.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final TaskChangeLogPort changeLogPort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 3 : Injection du TagPort
     * ═══════════════════════════════════════════════════════════════════
     * Permet de gérer les associations tag ↔ tâche (table task_tags)
     * lors de la création et de la modification des tâches.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final TagPort tagPort;

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TÂCHE
    // ═══════════════════════════════════════════════════════

    @Transactional
    public Task createTask(String title, String description, String currentUsername) {
        return createTask(title, description, currentUsername, null, null, null, null);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 3 : Création avec support des tags
     * ═══════════════════════════════════════════════════════════════════
     * Surcharge qui accepte une liste de tagIds à associer à la tâche
     * dès sa création. Le comportement existant (sans tags) est
     * préservé via la surcharge à 3 paramètres ci-dessus.
     * ═══════════════════════════════════════════════════════════════════
     */
    @Transactional
    public Task createTask(String title, String description, String currentUsername,
                           String priority, LocalDate dueDate, String assigneeId,
                           List<String> tagIds) {
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
        // PHASE 3 — FEATURE 3 : Association des tags à la création
        // ═══════════════════════════════════════════════════════════════════
        // Si des tagIds sont fournis, on les associe un par un à la tâche.
        // Chaque association crée une ligne dans la table task_tags.
        // On vérifie d'abord que le tag existe avant de l'associer.
        // ═══════════════════════════════════════════════════════════════════
        if (tagIds != null && !tagIds.isEmpty()) {
            for (String tagId : tagIds) {
                if (tagId != null && !tagId.isBlank()) {
                    try {
                        tagPort.addTagToTask(savedTask.id(), tagId);
                        log.debug("SERVICE : Tag {} associé à la tâche {}", tagId, savedTask.id());
                    } catch (Exception e) {
                        // Si le tag n'existe pas ou s'il est déjà associé, on logue
                        // mais on ne fait PAS échouer la création de la tâche
                        log.warn("SERVICE : Impossible d'associer le tag {} à la tâche {} — {}",
                                tagId, savedTask.id(), e.getMessage());
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 2 : Enregistrement des valeurs initiales
        // ═══════════════════════════════════════════════════════════════════
        // À la création, on enregistre les valeurs initiales comme changements
        // (oldValue = null, newValue = valeur initiale).
        // Cela permet de voir dans l'historique QUELLES étaient les valeurs
        // de départ de la tâche.
        // ═══════════════════════════════════════════════════════════════════
        List<TaskChangeLog> changes = new ArrayList<>();
        changes.add(TaskChangeLog.create(savedTask.id(), "title", null, savedTask.title(), currentUsername));
        if (savedTask.description() != null && !savedTask.description().isBlank()) {
            changes.add(TaskChangeLog.create(savedTask.id(), "description", null, savedTask.description(), currentUsername));
        }
        changes.add(TaskChangeLog.create(savedTask.id(), "status", null, savedTask.status().name(), currentUsername));
        changes.add(TaskChangeLog.create(savedTask.id(), "priority", null, savedTask.priority().name(), currentUsername));
        if (savedTask.dueDate() != null) {
            changes.add(TaskChangeLog.create(savedTask.id(), "dueDate", null, savedTask.dueDate().toString(), currentUsername));
        }
        if (savedTask.assigneeId() != null) {
            changes.add(TaskChangeLog.create(savedTask.id(), "assigneeId", null, savedTask.assigneeId(), currentUsername));
        }
        changeLogPort.saveAll(changes);

        // PHASE 2 — TÂCHE 4 : Audit via événement (post-commit)
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
    // MISE À JOUR AVEC RBAC + CHANGE LOGGING + TAGS
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 3 : Mise à jour avec synchronisation des tags
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE — COMPARAISON CHAMP PAR CHAMP :
     * Avant d'appliquer chaque modification, on compare l'ancienne
     * et la nouvelle valeur. Si elles sont différentes, on enregistre
     * le changement dans TaskChangeLog.
     *
     * PATTERN "COLLECTING PARAMETER" :
     * On accumule les changements dans une List<TaskChangeLog>,
     * puis on les sauvegarde en batch à la fin (une seule opération DB).
     *
     * SYNCHRONISATION DES TAGS :
     * Si tagIds est fourni (non null), on synchronise :
     * 1. On récupère les tags actuels de la tâche
     * 2. On calcule les tags à ajouter (nouveaux - actuels)
     * 3. On calcule les tags à retirer (actuels - nouveaux)
     * 4. On applique les ajouts et retraits
     * ═══════════════════════════════════════════════════════════════════
     */
    @Transactional
    public Optional<Task> updateTask(String taskId, String currentUsername, String currentRole,
                                     String title, String description, String priority, LocalDate dueDate,
                                     List<String> tagIds) {
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

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 2 : Collecte des changements
        // ═══════════════════════════════════════════════════════════════════
        List<TaskChangeLog> changes = new ArrayList<>();

        if (title != null && !title.isBlank() && !title.equals(existingTask.title())) {
            changes.add(TaskChangeLog.create(taskId, "title",
                    existingTask.title(), title, currentUsername));
            updatedTask = updatedTask.update(title, updatedTask.description());
            details.append(" — titre changé");
        }
        if (description != null && !description.equals(existingTask.description())) {
            changes.add(TaskChangeLog.create(taskId, "description",
                    existingTask.description(), description, currentUsername));
            updatedTask = updatedTask.update(updatedTask.title(), description);
            details.append(" — description modifiée");
        }
        if (priority != null) {
            Task.TaskPriority newPriority = Task.TaskPriority.valueOf(priority);
            if (newPriority != existingTask.priority()) {
                changes.add(TaskChangeLog.create(taskId, "priority",
                        existingTask.priority().name(), newPriority.name(), currentUsername));
                updatedTask = updatedTask.updatePriority(newPriority);
                details.append(" — priorité → ").append(newPriority.name());
            }
        }
        if (dueDate != null) {
            String oldDueDate = existingTask.dueDate() != null ? existingTask.dueDate().toString() : null;
            String newDueDate = dueDate.toString();
            if (!dueDate.equals(existingTask.dueDate())) {
                changes.add(TaskChangeLog.create(taskId, "dueDate",
                        oldDueDate, newDueDate, currentUsername));
                updatedTask = updatedTask.updateDueDate(dueDate);
                details.append(" — date d'échéance → ").append(dueDate);
            }
        }

        Task savedTask = persistencePort.save(updatedTask);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 3 : Synchronisation des tags
        // ═══════════════════════════════════════════════════════════════════
        // Si tagIds est fourni (non null), on synchronise les associations.
        // PRINCIPE : on compare les tags actuels avec les tags demandés,
        // puis on ajoute les nouveaux et on retire les anciens.
        // ═══════════════════════════════════════════════════════════════════
        if (tagIds != null) {
            // Récupérer les tags actuels de la tâche
            List<Tag> currentTags = tagPort.findTagsByTaskId(taskId);
            List<String> currentTagIds = currentTags.stream()
                    .map(Tag::id)
                    .toList();

            // Tags à ajouter : dans tagIds mais pas dans currentTagIds
            List<String> tagsToAdd = tagIds.stream()
                    .filter(id -> !currentTagIds.contains(id))
                    .toList();

            // Tags à retirer : dans currentTagIds mais pas dans tagIds
            List<String> tagsToRemove = currentTagIds.stream()
                    .filter(id -> !tagIds.contains(id))
                    .toList();

            // Appliquer les ajouts
            for (String tagIdToAdd : tagsToAdd) {
                try {
                    tagPort.addTagToTask(taskId, tagIdToAdd);
                    log.debug("SERVICE : Tag {} ajouté à la tâche {}", tagIdToAdd, taskId);
                } catch (Exception e) {
                    log.warn("SERVICE : Impossible d'ajouter le tag {} à la tâche {} — {}",
                            tagIdToAdd, taskId, e.getMessage());
                }
            }

            // Appliquer les retraits
            for (String tagIdToRemove : tagsToRemove) {
                try {
                    tagPort.removeTagFromTask(taskId, tagIdToRemove);
                    log.debug("SERVICE : Tag {} retiré de la tâche {}", tagIdToRemove, taskId);
                } catch (Exception e) {
                    log.warn("SERVICE : Impossible de retirer le tag {} de la tâche {} — {}",
                            tagIdToRemove, taskId, e.getMessage());
                }
            }

            if (!tagsToAdd.isEmpty() || !tagsToRemove.isEmpty()) {
                details.append(" — tags modifiés");
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 2 : Sauvegarde en batch des changements
        // ═══════════════════════════════════════════════════════════════════
        if (!changes.isEmpty()) {
            changeLogPort.saveAll(changes);
        }

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
    // CHANGEMENT DE STATUT AVEC RBAC + CHANGE LOGGING
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

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 2 : Enregistrement du changement de statut
        // ═══════════════════════════════════════════════════════════════════
        if (oldStatus != status) {
            TaskChangeLog change = TaskChangeLog.create(taskId, "status",
                    oldStatus.name(), status.name(), currentUsername);
            changeLogPort.save(change);
        }

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
    // ASSIGNATION DE TÂCHE (MANAGER/ADMIN uniquement) + CHANGE LOGGING
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

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — FEATURE 2 : Enregistrement du changement d'assignation
        // ═══════════════════════════════════════════════════════════════════
        String oldAssignee = existingTask.assigneeId();
        if ((oldAssignee == null && effectiveAssigneeId != null)
                || (oldAssignee != null && !oldAssignee.equals(effectiveAssigneeId))) {
            TaskChangeLog change = TaskChangeLog.create(taskId, "assigneeId",
                    oldAssignee, effectiveAssigneeId, currentUsername);
            changeLogPort.save(change);
        }

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

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : taskTitle récupéré avant suppression
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (BUG) :
     *   taskTitle était null dans l'événement d'audit de suppression.
     *   L'Activity Log affichait un titre vide pour les tâches supprimées.
     *   Cause : l'événement était publié avec null comme taskTitle.
     *
     * APRÈS :
     *   On récupère le titre de la tâche AVANT le soft-delete.
     *   La tâche existe encore à ce moment (soft-delete = flag, pas suppression physique).
     *   L'Activity Log affiche maintenant le titre correct de la tâche supprimée.
     * ═══════════════════════════════════════════════════════════════════
     */
    @Transactional
    public boolean deleteTask(String taskId, String currentUsername, String currentRole) {
        if ("ADMIN".equals(currentRole)) {
            Task task = persistencePort.findById(taskId).orElse(null);
            if (task == null) return false;

            // CORRECTION ACTIVITY : Récupérer le titre AVANT le soft-delete
            String taskTitle = task.title();

            persistencePort.softDelete(taskId);
            // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
            eventPublisher.publishAuditEvent(new TaskAuditEvent(
                    ActivityLog.Action.TASK_DELETED,
                    "Tâche '" + taskTitle + "' supprimée (ADMIN)",
                    currentUsername,
                    taskId,
                    taskTitle
            ));
            return true;
        }
        Task task = persistencePort.findByIdAndUserId(taskId, currentUsername).orElse(null);
        if (task == null) return false;

        // CORRECTION ACTIVITY : Récupérer le titre AVANT le soft-delete
        String taskTitle = task.title();

        persistencePort.softDelete(taskId);
        // PHASE 2 — TÂCHE 4 : Audit via événement post-commit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TASK_DELETED,
                "Tâche '" + taskTitle + "' supprimée par son créateur",
                currentUsername,
                taskId,
                taskTitle
        ));
        return true;
    }
}