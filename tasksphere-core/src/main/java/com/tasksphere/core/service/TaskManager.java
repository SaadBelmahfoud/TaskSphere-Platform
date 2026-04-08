package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    private final TaskPersistencePort persistencePort;
    // INJECTION DU NOUVEAU MÉGAPHONE
    private final EventPublisherPort eventPublisher;

    @Transactional
    public Task createTask(String title, String description) {
        log.info("SERVICE METIER : Début de la création de la tâche {}", title);

        Task taskToSave = Task.create(title, description);

        log.debug("SERVICE METIER : Demande de sauvegarde en BDD");
        Task savedTask = persistencePort.save(taskToSave);

        // APRES LA SAUVEGARDE, ON CRIE L'ÉVÉENEMENT !
        // Le service ne fait pas d'envoi d'email, il délègue.
        TaskCreatedEvent event = TaskCreatedEvent.of(savedTask.id(), savedTask.title());
        log.info("SERVICE METIER : Publication de l'événement de création");
        eventPublisher.publishTaskCreated(event);

        log.info("SERVICE METIER : Tâche créée avec succès. ID={}", savedTask.id());
        return savedTask;
    }

    // ... getAllTasks() reste identique
    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        log.info("SERVICE METIER : Demande au Port de récupérer toutes les tâches");
        return persistencePort.findAll();
    }
}