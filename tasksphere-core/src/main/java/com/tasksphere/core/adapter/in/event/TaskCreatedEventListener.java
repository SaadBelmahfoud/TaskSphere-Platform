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
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 1 — CORRECTION P1-8 : Suppression du Thread.sleep(3000)
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   Thread.sleep(3000) pour simuler un envoi d'email lent.
 *   → Bloque un thread du pool pendant 3 secondes
 *   → Si 10 tâches créées simultanément → 10 threads bloqués
 *   → Thread starvation → risque OutOfMemoryError
 *   → Le sleep ne simule rien de réel en production
 *
 * APRÈS (CORRECTION) :
 *   Suppression du Thread.sleep(). Le listener loggue simplement
 *   l'événement de manière asynchrone. Quand l'intégration email
 *   sera réelle, le traitement sera un appel HTTP asynchrone
 *   (ex: vers SendGrid, MailJet) qui ne bloquera pas.
 *
 * PRINCIPE : En production, les listeners asynchrones doivent être :
 *   1. RAPIDES : déléguer le travail lourd à un service externe
 *   2. NON-BLOQUANTS : jamais de Thread.sleep() ni d'I/O synchrone long
 *   3. RÉSILIENTS : try-catch pour ne jamais faire échouer l'événement
 * ═══════════════════════════════════════════════════════════════════
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
            /*
             * ═══════════════════════════════════════════════════════
             * PHASE 1 — P1-8 : Traitement asynchrone sans blocage
             * ═══════════════════════════════════════════════════════
             * AVANT : Thread.sleep(3000) pour simuler l'envoi d'email
             * APRÈS : Traitement immédiat (logging + préparation)
             *
             * Quand l'intégration email sera implémentée :
             * - Appel asynchrone à l'API email (SendGrid, MailJet, etc.)
             * - Pas de Thread.sleep() : l'appel HTTP est naturellement async
             * - Le pool de threads custom (AsyncConfig) contrôlera
             *   le nombre de threads et la file d'attente
             * ═══════════════════════════════════════════════════════
             */
            log.info("ÉCOUTEUR EVENT : Notification de création traitée pour la tâche {}", event.taskId());

            // TODO : Intégrer l'envoi d'email réel ici
            // Exemple : emailService.sendTaskCreatedNotification(event)

        } catch (Exception e) {
            // Fail-safe : l'erreur dans le listener ne doit JAMAIS
            // faire échouer l'opération principale (création de tâche)
            log.error("ÉCOUTEUR EVENT : Erreur lors du traitement pour la tâche {}", event.taskId(), e);
        }

        log.info("ÉCOUTEUR EVENT : [FIN] Traitement terminé pour la tâche {}", event.taskId());
    }
}