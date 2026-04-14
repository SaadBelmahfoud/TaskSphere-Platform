package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.port.out.UserInformationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * ====================================================================
 * SERVICE MÉTIER (Le Cœur du Domaine "Task")
 * ====================================================================
 *
 * @Slf4j : Génère un logger pour tracer le flux.
 * @Service : Dit à Spring "Je suis un Bean métier, gère ma vie".
 * @RequiredArgsConstructor : Génère un constructeur avec tous les attributs 'final'.
 *                          C'est la seule façon propre de faire de l'Injection de Dépendances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    // ====================================================================
    // DÉPENDANCES (Les Ports Sortants)
    // ====================================================================
    // Remarque comme on n'importe JAMAIS les classes des adaptateurs (ex: IamUserAdapter, TaskPersistenceAdapter).
    // On ne dépend que d'interfaces (Ports). C'est le "D" de SOLID (Dependency Inversion).

    private final TaskPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;

    /*
     * NOUVEAUTÉ V8 : Le Port vers le module IAM.
     * Grâce à l'Architecture Hexagonale, ce Service n'a aucune idée si l'information
     * va venir d'un appel HTTP, d'une base de données, ou (comme c'est le cas ici) d'un
     * appel direct en mémoire Java. Il est "aveugle" à l'infrastructure.
     */
    private final UserInformationPort userInformationPort;

    // ====================================================================
    // MÉTHODES MÉTIER
    // ====================================================================

    /**
     * Création d'une nouvelle tâche.
     *
     * @param title Le titre de la tâche
     * @param description La description
     * @param requestedByUsername Le nom de l'utilisateur qui fait la demande (extrait du JWT par le Controller)
     * @return La tâche créée, enrichie de son ID
     */
    @Transactional
    public Task createTask(String title, String description, String requestedByUsername) {
        log.info("SERVICE METIER : Début de la création de la tâche '{}' par {}", title, requestedByUsername);

        // -------------------------------------------------------------
        // ÉTAPE V8 : Enrichissement métier via le module IAM
        // -------------------------------------------------------------
        // On demande au Port d'aller chercher les infos de l'utilisateur.
        // L'Adapter (IamUserAdapter) fera le pont en coulisses.
        UserInfo userInfo = userInformationPort.getUserInfo(requestedByUsername);

        log.info("SERVICE METIER : Profil utilisateur récupéré -> Nom: {}, Rôle: {}",
                userInfo.name(), userInfo.userRole());

        // -------------------------------------------------------------
        // ÉTAPE V1/V2 : Création de l'objet de valeur (Domaine pur)
        // -------------------------------------------------------------
        Task taskToSave = Task.create(title, description);

        // -------------------------------------------------------------
        // ÉTAPE V3/V4 : Persistance via le Port de sortie BDD
        // -------------------------------------------------------------
        log.debug("SERVICE METIER : Demande de sauvegarde en BDD");
        Task savedTask = persistencePort.save(taskToSave);

        // -------------------------------------------------------------
        // ÉTAPE V6 : Publication de l'événement de création
        // -------------------------------------------------------------
        TaskCreatedEvent event = TaskCreatedEvent.of(savedTask.id(), savedTask.title());
        log.info("SERVICE METIER : Publication de l'événement de création");
        eventPublisher.publishTaskCreated(event);

        log.info("SERVICE METIER : Tâche créée avec succès. ID={}", savedTask.id());
        return savedTask;
    }

    /**
     * Récupération de toutes les tâches.
     */
    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        log.info("SERVICE METIER : Demande au Port de récupérer toutes les tâches");
        return persistencePort.findAll();
    }
}