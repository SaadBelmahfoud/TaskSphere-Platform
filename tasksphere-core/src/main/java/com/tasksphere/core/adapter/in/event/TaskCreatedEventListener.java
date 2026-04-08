package com.tasksphere.core.adapter.in.event;

import com.tasksphere.core.domain.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/*
 * L'ÉCOUTEUR D'ÉVÉNEMENTS (Le Worker).
 *
 * @Component : Spring va l'enregistrer et l'écouter.
 * @Async : LA CLÉ DE LA V6 !!!
 * Sans cette annotation, le fil d'exécution (Thread) qui traite la requête HTTP
 * s'arrêterait ici pour attendre la fin de cette méthode (Synchrone).
 * AVEC @Async, Spring va prendre ce code et l'exécuter sur UN AUTRE THREAD (en parallèle).
 * La réponse HTTP sera renvoyée au client IMMÉDIATEMENT.
 */
@Slf4j
@Component
public class TaskCreatedEventListener {

    @Async
    @EventListener
    public void handleTaskCreatedEvent(TaskCreatedEvent event) {
        log.info("ÉCOUTEUR EVENT : [DÉBUT] Réception asynchrone de l'événement pour la tâche {}. Je travaille sur le Thread : {}",
                event.taskId(), Thread.currentThread().getName());

        try {
            // On simule un traitement TRÈS LENT (ex: appel à une API d'envoi d'email qui met 3 secondes)
            log.info("ÉCOUTEUR EVENT : Envoi d'un email de confirmation en cours... (Simulation de 3 secondes)");
            Thread.sleep(3000); // Pause de 3 secondes
            log.info("ÉCOUTEUR EVENT : Email envoyé avec succès pour la tâche {} !", event.taskId());
        } catch (InterruptedException e) {
            log.error("ÉCOUTEUR EVENT : Erreur lors de l'envoi de l'email pour la tâche {}", event.taskId(), e);
        }

        log.info("ÉCOUTEUR EVENT : [FIN] Traitement terminé pour la tâche {}", event.taskId());
    }
}