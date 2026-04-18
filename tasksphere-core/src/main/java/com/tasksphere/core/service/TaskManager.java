package com.tasksphere.core.service;

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

/*
 * ====================================================================
 * SERVICE MÉTIER : TASK (Le cœur du domaine)
 * ====================================================================
 *
 * PRINCIPE CLEAN ARCHITECTURE / HEXAGONALE :
 * Le service ne connaît que des interfaces (Ports), jamais des implémentations.
 * Il contient toute la logique métier mais aucune logique technique (JPA, HTTP...).
 *
 * PRINCIPE DDD (Domain-Driven Design) :
 * Le service est le "Use Case" ou "Application Service".
 * Il orchestre les interactions entre le domaine (Task), les ports entrants
 * (Controller) et les ports sortants (Persistence, Events).
 *
 * DÉPENDANCES DU SERVICE :
 * - TaskPersistencePort : pour sauvegarder/lire les tâches (port sortant)
 * - EventPublisherPort : pour publier des événements (port sortant)
 * - UserInformationPort : pour récupérer les infos utilisateur (port sortant)
 *
 * PRINCIPE @Transactional :
 * - (readOnly = true) : optimisation Hibernate, pas de dirty checking
 * - (par défaut) : ouvre une transaction, commit à la fin, rollback si exception
 *
 * SPRINT 1 : CRUD complet + ownership + changement de statut/priorité + soft delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final UserInformationPort userInformationPort;

    /**
     * Créer une nouvelle tâche pour un utilisateur (version simplifiée).
     * Utilise les valeurs par défaut : priorité MEDIUM, pas de dueDate.
     * Délègue à la version complète avec null pour priority et dueDate.
     */
    @Transactional
    public Task createTask(String title, String description, String currentUsername) {
        return createTask(title, description, currentUsername, null, null);
    }

    /**
     * Créer une nouvelle tâche pour un utilisateur (version complète).
     * Le titre est obligatoire, la description optionnelle.
     * La priorité et la dueDate sont optionnelles (valeurs par défaut si null).
     *
     * PRINCIPE D'ENCHAÎNEMENT :
     * 1. Récupérer les infos utilisateur (port IAM)
     * 2. Créer la tâche avec Task.create() (factory method du domaine)
     * 3. Appliquer les options (priority, dueDate) via les méthodes "with"
     * 4. Persister en BDD (port persistance)
     * 5. Publier l'événement de création (port events)
     *
     * @param title          Titre de la tâche (obligatoire, validé par le controller)
     * @param description    Description (optionnelle, normalisée en "" si null)
     * @param currentUsername Username de l'utilisateur connecté (sert de userId)
     * @param priority       Priorité ("LOW", "MEDIUM", "HIGH", "CRITICAL") ou null = MEDIUM
     * @param dueDate        Date d'échéance ou null = pas de date
     */
    @Transactional
    public Task createTask(String title, String description, String currentUsername,
                           String priority, LocalDate dueDate) {
        log.info("SERVICE : Création de la tâche '{}' par {}", title, currentUsername);

        // Récupérer les infos utilisateur depuis le module IAM
        var userInfo = userInformationPort.getUserInfo(currentUsername);

        // Créer la tâche avec les valeurs par défaut du domaine
        Task taskToSave = Task.create(
                title,
                description != null ? description : "",
                currentUsername
        );

        // Appliquer la priorité si fournie (sinon garde MEDIUM par défaut du domaine)
        if (priority != null && !priority.isBlank()) {
            taskToSave = taskToSave.updatePriority(Task.TaskPriority.valueOf(priority));
        }

        // Appliquer la dueDate si fournie (sinon garde null par défaut du domaine)
        if (dueDate != null) {
            taskToSave = taskToSave.updateDueDate(dueDate);
        }

        // Persister en BDD via le port de persistance
        Task savedTask = persistencePort.save(taskToSave);

        // Publier l'événement de création (pour les listeners / audit / notifications)
        eventPublisher.publishTaskCreated(TaskCreatedEvent.of(savedTask.id(), savedTask.title()));

        log.info("SERVICE : Tâche créée avec succès (id: {}, user: {}, priority: {})",
                savedTask.id(), currentUsername, savedTask.priority());
        return savedTask;
    }

    /**
     * Lister les tâches de l'utilisateur connecté avec pagination.
     * Les tâches supprimées (soft delete) sont automatiquement exclues.
     *
     * PRINCIPE DE PAGINATION :
     * PageRequest.of(page, size, sort) crée un objet Pageable avec :
     * - page : index de la page (0-based)
     * - size : nombre d'éléments par page
     * - sort : ordre de tri (ici par createdAt descendant)
     *
     * PRINCIPE @Transactional(readOnly = true) :
     * Optimisation Hibernate : pas de dirty checking, pas de snapshot des entités.
     * Réduit la consommation mémoire pour les lectures.
     */
    @Transactional(readOnly = true)
    public Page<Task> getMyTasks(String currentUsername, int page, int size) {
        log.info("SERVICE : Liste des tâches de {} (page: {}, size: {})", currentUsername, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return persistencePort.findByUserId(currentUsername, pageable);
    }

    /**
     * Récupérer une tâche par son ID.
     * Vérifie que la tâche appartient à l'utilisateur connecté (ownership).
     *
     * PRINCIPE D'OWNERSHIP (RBAC) :
     * On utilise findByIdAndUserId() au lieu de findById() pour garantir
     * que l'utilisateur ne peut accéder qu'à ses propres tâches.
     * Si on utilisait findById(), n'importe quel utilisateur authentifié
     * pourrait voir les tâches des autres (faille de sécurité).
     */
    @Transactional(readOnly = true)
    public Optional<Task> getTaskById(String taskId, String currentUsername) {
        log.info("SERVICE : Recherche tâche {} pour l'utilisateur {}", taskId, currentUsername);
        return persistencePort.findByIdAndUserId(taskId, currentUsername);
    }

    /**
     * Mettre à jour une tâche (titre, description, priorité, dueDate).
     * Seul le propriétaire peut modifier sa tâche (ownership / RBAC).
     *
     * PRINCIPE DE MISE À JOUR PARTIELLE :
     * Seuls les champs non null sont modifiés. Cela permet au client
     * d'envoyer uniquement les champs qu'il veut modifier.
     * C'est le pattern "Partial Update" (PATCH-like avec PUT).
     */
    @Transactional
    public Optional<Task> updateTask(String taskId, String currentUsername,
                                     String title, String description,
                                     String priority, LocalDate dueDate) {
        log.info("SERVICE : Mise à jour de la tâche {} par {}", taskId, currentUsername);

        // 1. Vérifier que la tâche existe et appartient à l'utilisateur
        Task existingTask = persistencePort.findByIdAndUserId(taskId, currentUsername)
                .orElse(null);

        if (existingTask == null) {
            log.warn("SERVICE : Tâche {} non trouvée ou non autorisée pour {}", taskId, currentUsername);
            return Optional.empty();
        }

        // 2. Appliquer les modifications (seuls les champs non null sont modifiés)
        Task updatedTask = existingTask;

        if (title != null && !title.isBlank()) {
            updatedTask = updatedTask.update(title, updatedTask.description());
        }
        if (description != null) {
            updatedTask = updatedTask.update(updatedTask.title(), description);
        }
        if (priority != null) {
            updatedTask = updatedTask.updatePriority(Task.TaskPriority.valueOf(priority));
        }
        if (dueDate != null) {
            updatedTask = updatedTask.updateDueDate(dueDate);
        }

        // 3. Sauvegarder via le port
        Task savedTask = persistencePort.save(updatedTask);
        log.info("SERVICE : Tâche {} mise à jour avec succès", taskId);
        return Optional.of(savedTask);
    }

    /**
     * Changer le statut d'une tâche (TODO → DOING → DONE).
     * Si le nouveau statut est DONE, completedAt est automatiquement renseigné.
     *
     * PRINCIPE D'AUTOMATISATION :
     * Le domaine Task.updateStatus() gère automatiquement completedAt.
     * Le service n'a pas besoin de le faire manuellement.
     */
    @Transactional
    public Optional<Task> updateTaskStatus(String taskId, String currentUsername, String newStatus) {
        log.info("SERVICE : Changement statut tâche {} → {} par {}", taskId, newStatus, currentUsername);

        Task existingTask = persistencePort.findByIdAndUserId(taskId, currentUsername)
                .orElse(null);

        if (existingTask == null) {
            log.warn("SERVICE : Tâche {} non trouvée ou non autorisée", taskId);
            return Optional.empty();
        }

        Task.TaskStatus status = Task.TaskStatus.valueOf(newStatus);
        Task updatedTask = existingTask.updateStatus(status);
        Task savedTask = persistencePort.save(updatedTask);

        log.info("SERVICE : Tâche {} → statut {} (completedAt: {})",
                taskId, status, savedTask.completedAt());
        return Optional.of(savedTask);
    }

    /**
     * Supprimer (archiver) une tâche (soft delete).
     * La tâche n'est pas supprimée en BDD, juste marquée comme archivée.
     *
     * PRINCIPE DU SOFT DELETE :
     * - Avantages : traçabilité, restauration possible, intégrité référentielle
     * - deletedAt est renseigné → la tâche est filtrée dans toutes les requêtes
     */
    @Transactional
    public boolean deleteTask(String taskId, String currentUsername) {
        log.info("SERVICE : Suppression (soft delete) de la tâche {} par {}", taskId, currentUsername);

        Optional<Task> task = persistencePort.findByIdAndUserId(taskId, currentUsername);
        if (task.isEmpty()) {
            log.warn("SERVICE : Tâche {} non trouvée ou non autorisée", taskId);
            return false;
        }

        persistencePort.softDelete(taskId);
        log.info("SERVICE : Tâche {} archivée avec succès", taskId);
        return true;
    }
}