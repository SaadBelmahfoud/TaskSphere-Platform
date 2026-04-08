package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * ARCHITECTURE HEXAGONALE : L'ADAPTATEUR SORTANT
 *
 * POURQUOI @Component ET PAS @Service ?
 * Parce que cette classe n'est PAS une logique métier. C'est de la mécanique technique.
 * L'annotation @Component dit à Spring : "Je suis un rouage technique, enregistre-moi".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPersistenceAdapter implements TaskPersistencePort {

    // L'adaptateur a le droit d'utiliser l'outil technique JPA
    private final TaskRepository taskRepository;

    @Override
    public Task save(Task task) {
        log.debug("ADAPTATEUR JPA : Traduction du Domaine vers l'Entité pour sauvegarde SQL");

        // 1. Traduction Domaine -> Entité
        TaskEntity entity = new TaskEntity(task.title(), task.description());

        // 2. Appel technique à la base de données
        TaskEntity savedEntity = taskRepository.save(entity);

        // 3. Traduction Entité -> Domaine (pour respecter le contrat du Port)
        return new Task(savedEntity.getId(), savedEntity.getTitle(), savedEntity.getDescription());
    }

    @Override
    public List<Task> findAll() {
        log.debug("ADAPTATEUR JPA : Récupération SQL et traduction vers le Domaine");

        return taskRepository.findAll().stream()
                .map(entity -> new Task(entity.getId(), entity.getTitle(), entity.getDescription()))
                .toList();
    }
}