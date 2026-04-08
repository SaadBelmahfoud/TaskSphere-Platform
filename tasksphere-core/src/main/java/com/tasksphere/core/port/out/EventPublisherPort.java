package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.event.TaskCreatedEvent;

/*
 * LE PORT DE PUBLICATION (Output Port).
 *
 * Le Service utilise ce mégaphone sans savoir qui est à l'autre bout.
 * Est-ce un système de fichier ? Kafka ? Un simple logger ? Il s'en fout.
 */
public interface EventPublisherPort {
    void publishTaskCreated(TaskCreatedEvent event);
}