package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.entity.TaskEntity;
import com.tasksphere.core.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor // Injecte TaskRepository automatiquement
public class TaskManager {

    // Plus de ArrayList ! On utilise le Repository (qui parle à la BDD)
    private final TaskRepository taskRepository;

    /*
     * @Transactional : Concept avancé mais indispensable.
     * Ça dit à Spring : "Ouvre une connexion à la base de données au début de cette méthode,
     * et ferme-la (en validant ou en annulant) à la fin".
     */
    @Transactional
    public Task createTask(String title, String description) {
        log.info("Demande de création en BDD pour : {}", title);

        // 1. Conversion DTO/Domaine -> Entité (pour la BDD)
        TaskEntity entity = new TaskEntity(title, description);

        // 2. Sauvegarde en BDD (le champ ID va se remplir tout seul grâce au @GeneratedValue)
        TaskEntity savedEntity = taskRepository.save(entity);

        // 3. Conversion Entité -> Domaine (pour le reste de l'application)
        return new Task(savedEntity.getId(), savedEntity.getTitle(), savedEntity.getDescription());
    }

    @Transactional(readOnly = true) // Optimisation : on précise qu'on ne modifie pas la BDD
    public List<Task> getAllTasks() {
        log.info("Récupération de toutes les tâches depuis la BDD");

        // On récupère des Entités, et on les mappe une par une en objets Domaine
        return taskRepository.findAll().stream()
                .map(entity -> new Task(entity.getId(), entity.getTitle(), entity.getDescription()))
                .toList();
    }
}