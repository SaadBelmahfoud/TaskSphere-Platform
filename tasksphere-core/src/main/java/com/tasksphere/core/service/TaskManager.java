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

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TÂCHE — CORRECTIONS B1, B9, B10
    // ═══════════════════════════════════════════════════════

    /**
     * Crée une tâche avec tous les paramètres optionnels.
     *
     * CORRECTIONS APPORTÉES :
     * ──────────────────────
     * B1 : valueOf() maintenant protégé par toUpperCase() + try-catch
     *      → Plus de crash si le frontend envoie "high" au lieu de "HIGH"
     *
     * B9 : Vérification RBAC avant d'assigner (ADMIN/MANAGER seulement)
     *      → Un USER ne peut plus créer une tâche déjà assignée
     *
     * B10 : getUserInfo() retiré (était appelé mais jamais utilisé)
     *       → Suppression de l'appel DB inutile
     */
    @Transactional
    public Task createTask(String title, String description, String currentUsername,
                           String priority, LocalDate dueDate, String assigneeId) {
        log.info("SERVICE : Création de la tâche '{}' par {}", title, currentUsername);

        Task taskToSave = Task.create(title, description != null ? description : "", currentUsername);

        // CORRECTION B1 : valueOf() protégé par toUpperCase() + try-catch
        // ──────────────────────────────────────────────────────────────
        // AVANT : Task.TaskPriority.valueOf(priority) → crash si "high"
        // APRÈS : valueOf(priority.toUpperCase()) + catch → null si invalide
        // Le comportement est maintenant cohérent avec parsePriority() du contrôleur
        if (priority != null && !priority.isBlank()) {
            try {
                taskToSave = taskToSave.updatePriority(
                        Task.TaskPriority.valueOf(priority.toUpperCase()));  // ← CORRECTION B1
            } catch (IllegalArgumentException e) {
                log.warn("SERVICE : Priorité invalide '{}' ignorée, utilisation de MEDIUM", priority);
                // La priorité par défaut (MEDIUM) est conservée
            }
        }
        if (dueDate != null) {
            taskToSave = taskToSave.updateDueDate(dueDate);
        }

        // CORRECTION B9 : Vérification RBAC pour l'assignation à la création
        // ──────────────────────────────────────────────────────────────
        // AVANT : Aucune vérification → n'importe quel USER pouvait assigner
        // APRÈS : Seuls ADMIN et MANAGER peuvent assigner à la création
        // Le TaskController vérifie déjà le rôle, mais le service doit aussi
        // être protégé (principe de défense en profondeur)
        if (assigneeId != null && !assigneeId.isBlank()) {
            var userInfo = userInformationPort.getUserInfo(currentUsername);
            String userRole = userInfo.userRole();
            // Retirer le préfixe "ROLE_" si présent (Spring Security convention)
            String cleanRole = userRole.startsWith("ROLE_")
                    ? userRole.substring(5) : userRole;
            if (!"ADMIN".equals(cleanRole) && !"MANAGER".equals(cleanRole)) {
                log.warn("RBAC : User {} (rôle: {}) a tenté d'assigner une tâche sans permission",
                        currentUsername, cleanRole);
                // On ignore l'assignation au lieu de crasher — la tâche est créée sans assignation
            } else {
                taskToSave = taskToSave.assignTo(assigneeId);
            }
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
        // CORRECTION BUG 1 : Utiliser findByUserIsOwnerOrAssignee au lieu de findByUserId
        // ──────────────────────────────────────────────────────────────────────────────
        // AVANT : persistencePort.findByUserId(currentUsername, pageable)
        //   → Ne retournait QUE les tâches créées par l'utilisateur
        //   → Les tâches assignées à l'utilisateur n'apparaissaient PAS
        //
        // APRÈS : persistencePort.findByUserIsOwnerOrAssignee(currentUsername, pageable)
        //   → Retourne les tâches créées PAR l'utilisateur ET les tâches assignées À l'utilisateur
        //   → Un USER voit maintenant toutes les tâches auxquelles il est impliqué
        return persistencePort.findByUserIsOwnerOrAssignee(currentUsername, pageable);
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
     * → Utilise searchTasks() standard (sans filtre user)
     *
     * USER :
     * → Recherche LIMITÉE aux tâches créées + assignées
     * → CORRECTION BUG 1 : Utilise searchTasksForUser() (UNE SEULE requête)
     *   au lieu de l'ancienne approche cassée qui faisait DEUX requêtes
     *   et fusionnait les résultats manuellement.
     *
     * ═══════════════════════════════════════════════════════════
     * CORRECTION BUG 1 — Passage de 2 requêtes à 1 seule requête
     * ═══════════════════════════════════════════════════════════
     *
     * AVANT (APPROCHE CASSÉE) :
     * ────────────────────────
     * Pour un USER, on faisait :
     * 1. searchTasks(ownedCriteria)    → Page<Task> ownedTasks
     * 2. searchTasks(assignedCriteria) → Page<Task> assignedTasks
     * 3. Stream.concat(owned, assigned).distinct().toList()
     * 4. totalElements = Math.max(owned.total, assigned.total) → FAUX !
     *
     * PROBLÈMES :
     * - totalElements incorrect → pagination cassée
     * - Fusion de 2 pages ≠ 1 page correcte
     * - Doublons possibles (gérés par .distinct() mais coûteux)
     * - 2 requêtes SQL au lieu d'1
     *
     * APRÈS (APPROCHE CORRIGÉE) :
     * ──────────────────────────
     * Pour un USER, on fait :
     * 1. searchTasksForUser(username, criteria, pageable)
     *    → UNE SEULE requête SQL avec (userId = :username OR assigneeId = :username)
     *    → Pagination EXACTE
     *    → Pas de doublons
     *    → 1 seule requête SQL
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
            // ADMIN/MANAGER : Recherche globale (pas de filtre utilisateur)
            return persistencePort.searchTasks(criteria, pageable);
        } else {
            // USER : Recherche limitée aux tâches où l'utilisateur est impliqué
            // CORRECTION BUG 1 : Utilise searchTasksForUser() (1 seule requête)
            // au lieu de l'ancienne approche cassée (2 requêtes + fusion manuelle)
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
        // CORRECTION BUG 1 : Utiliser findByIdAndUserIsOwnerOrAssignee au lieu de findByIdAndUserId
        // ────────────────────────────────────────────────────────────────────────────────────────
        // AVANT : persistencePort.findByIdAndUserId(taskId, currentUsername)
        //   → Un USER qui est ASSIGNATAIRE (mais pas créateur) recevait un 404
        //   → La tâche existait en BDD mais n'était pas accessible à l'assignataire
        //
        // APRÈS : persistencePort.findByIdAndUserIsOwnerOrAssignee(taskId, currentUsername)
        //   → Un USER peut voir la tâche s'il est créateur (userId) OU assignataire (assigneeId)
        //   → Cela permet à un utilisateur assigné de voir le détail de la tâche
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
            // CORRECTION BUG 1 : MANAGER peut modifier toutes les tâches
            existingTask = persistencePort.findById(taskId).orElse(null);
        } else {
            // CORRECTION BUG 1 : USER peut modifier ses tâches créées ET les tâches assignées
            // ──────────────────────────────────────────────────────────────────────────
            // AVANT : persistencePort.findByIdAndUserId(taskId, currentUsername)
            //   → Un USER qui est ASSIGNATAIRE ne pouvait pas modifier la tâche
            //
            // APRÈS : persistencePort.findByIdAndUserIsOwnerOrAssignee(taskId, currentUsername)
            //   → Un USER peut modifier la tâche s'il est créateur OU assignataire
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
            logActivity(ActivityLog.Action.TASK_UNASSIGNED,
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