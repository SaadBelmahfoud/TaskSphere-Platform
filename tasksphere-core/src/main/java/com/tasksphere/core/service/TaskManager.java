package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * SERVICE METIER (CLEAN ARCHITECTURE)
 *
 * OBSERVE BIEN LES IMPORTS :
 * Il n'y a PLUS AUCUN IMPORT VERS LE PACKAGE "entity" OU "repository" !
 * Le Service est devenu "Aveugle" face à la base de données.
 * Il ne connaît que son Domaine (Task) et son Contrat (TaskPersistencePort).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManager {

    // On n'injecte plus le Repository technique, on injecte le Port (le contrat)
    private final TaskPersistencePort persistencePort;

    @Transactional
    public Task createTask(String title, String description) {
        log.info("SERVICE METIER : Début de la création de la tâche {}", title);

        // Création de l'objet métier pur
        Task taskToSave = Task.create(title, description);

        log.info("SERVICE METIER : Demande au Port de sauvegarder (Peu importe comment)");
        // Le Service ne sait pas si c'est du SQL, du MongoDB ou un fichier texte.
        Task savedTask = persistencePort.save(taskToSave);

        log.info("SERVICE METIER : Tâche sauvégardée avec succès. ID={}", savedTask.id());
        return savedTask;
    }

    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        log.info("SERVICE METIER : Demande au Port de récupérer toutes les tâches");
        return persistencePort.findAll();
    }
}