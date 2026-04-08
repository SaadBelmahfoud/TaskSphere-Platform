package com.tasksphere.core.domain.event;

import java.time.Instant;

/*
 * L'ÉVÉNEMENT MÉTIER (Domain Event).
 *
 * Un événement représente UN FAIT passé. C'est pour ça qu'on utilise l'immutabilité (Record).
 * Remarque le nom au passé : "TaskCreated" (Tâche créée), PAS "CreateTask".
 * Il contient toutes les infos nécessaires pour que celui qui écoute puisse réagir.
 */
public record TaskCreatedEvent(
        String taskId,
        String title,
        Instant occurredOn // Horodatage précis de l'événement
) {
    // Factory method pour générer la date automatiquement au moment de la création
    public static TaskCreatedEvent of(String taskId, String title) {
        return new TaskCreatedEvent(taskId, title, Instant.now());
    }
}