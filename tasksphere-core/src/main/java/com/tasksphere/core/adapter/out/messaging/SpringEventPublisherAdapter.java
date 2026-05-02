package com.tasksphere.core.adapter.out.messaging;

import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.domain.event.TaskCreatedEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE MESSAGING : SpringEventPublisherAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * Implémente EventPublisherPort en utilisant le mécanisme d'événements
 * de Spring (ApplicationEventPublisher).
 *
 * PRINCIPE : L'adaptateur traduit l'appel du port en appel Spring.
 * Le domaine ne connaît PAS ApplicationEventPublisher — il ne voit
 * que l'interface EventPublisherPort.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 4 : Ajout de publishAuditEvent()
 * ═══════════════════════════════════════════════════════════════════
 *
 * La méthode publishAuditEvent() publie un événement Spring qui sera
 * attrapé par @TransactionalEventListener dans TaskAuditEventListener.
 * Spring gère automatiquement le cycle de vie de la transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishTaskCreated(TaskCreatedEvent event) {
        log.debug("ADAPTATEUR MESSAGING : Publication TaskCreatedEvent pour la tâche {}", event.taskId());
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 4 : Publication d'un événement d'audit
     * ═══════════════════════════════════════════════════════════════════
     *
     * Publie l'événement via ApplicationEventPublisher.
     * Le @TransactionalEventListener(phase = AFTER_COMMIT) dans
     * TaskAuditEventListener sera déclenché APRÈS le commit de
     * la transaction courante.
     *
     * NOTE : L'événement est publié SYNCHRONEMENT dans la même thread,
     * mais le listener ne l'exécutera qu'APRÈS le commit grâce
     * à @TransactionalEventListener.
     */
    @Override
    public void publishAuditEvent(TaskAuditEvent event) {
        log.debug("ADAPTATEUR MESSAGING : Publication TaskAuditEvent — action={}, actor={}",
                event.action(), event.username());
        applicationEventPublisher.publishEvent(event);
    }
}