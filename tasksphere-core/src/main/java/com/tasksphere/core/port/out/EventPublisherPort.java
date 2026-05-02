package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.domain.event.TaskCreatedEvent;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Publication d'événements (Contrat du domaine)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE DDD (Domain-Driven Design) :
 * Le domaine publie des événements pour signaler que quelque chose
 * s'est produit. Les adaptateurs d'entrée (listeners) réagissent.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 4 : Ajout de publishAuditEvent()
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVELLE MÉTHODE : publishAuditEvent(TaskAuditEvent)
 * → Publie un événement d'audit qui sera traité APRÈS le commit
 *   de la transaction métier par TaskAuditEventListener.
 * → Cela garantit que l'audit ne peut JAMAIS faire échouer
 *   l'opération métier (fiabilité).
 */
public interface EventPublisherPort {

    /**
     * Publie un événement de création de tâche.
     * Utilisé pour déclencher des actions asynchrones (notifications, etc.)
     * après la création d'une tâche.
     */
    void publishTaskCreated(TaskCreatedEvent event);

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 4 : Publication d'un événement d'audit
     * ═══════════════════════════════════════════════════════════════════
     *
     * Publie un événement d'audit qui sera traité par
     * TaskAuditEventListener APRÈS le commit de la transaction.
     *
     * Le listener utilise @TransactionalEventListener(phase = AFTER_COMMIT)
     * pour garantir que l'audit n'est enregistré QUE si l'opération
     * métier a réussi et est commitée.
     *
     * @param event L'événement d'audit contenant les détails de l'action
     */
    void publishAuditEvent(TaskAuditEvent event);
}