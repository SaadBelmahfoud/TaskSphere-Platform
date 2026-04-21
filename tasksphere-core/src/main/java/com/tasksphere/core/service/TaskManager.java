package com.tasksphere.core.service;

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
 * règles de gestion des tâches, y compris le RBAC.
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final UserInformationPort userInformationPort;

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
        if (title != null && !title.isBlank())
            updatedTask = updatedTask.update(title, updatedTask.description());
        if (description != null)
            updatedTask = updatedTask.update(updatedTask.title(), description);
        if (priority != null)
            updatedTask = updatedTask.updatePriority(Task.TaskPriority.valueOf(priority));
        if (dueDate != null)
            updatedTask = updatedTask.updateDueDate(dueDate);

        return Optional.of(persistencePort.save(updatedTask));
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

        Task.TaskStatus status = Task.TaskStatus.valueOf(newStatus);
        return Optional.of(persistencePort.save(existingTask.updateStatus(status)));
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
        return Optional.of(persistencePort.save(existingTask.assignTo(effectiveAssigneeId)));
    }

    // ═══════════════════════════════════════════════════════
    // SOFT DELETE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Transactional
    public boolean deleteTask(String taskId, String currentUsername, String currentRole) {
        if ("ADMIN".equals(currentRole)) {
            if (persistencePort.findById(taskId).isEmpty()) return false;
            persistencePort.softDelete(taskId);
            return true;
        }
        if (persistencePort.findByIdAndUserId(taskId, currentUsername).isEmpty()) return false;
        persistencePort.softDelete(taskId);
        return true;
    }
}