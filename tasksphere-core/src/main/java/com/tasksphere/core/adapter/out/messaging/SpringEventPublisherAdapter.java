package com.tasksphere.core.adapter.out.messaging;

import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/*
 * L'ADAPTATEUR DE MESSAGERIE.
 *
 * @Component : C'est de l'infrastructure technique.
 * ApplicationEventPublisher : C'est l'outil natif de Spring pour diffuser des événements
 * dans la même JVM (Application Locale). On utilisera Kafka plus tard pour l'envoyer à d'autres microservices.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishTaskCreated(TaskCreatedEvent event) {
        log.info("ADAPTATEUR MESSAGERIE : Diffusion de l'événement Spring pour la tâche {}", event.taskId());
        // On demande à Spring de propulser l'objet Event dans toute l'application
        applicationEventPublisher.publishEvent(event);
    }
}