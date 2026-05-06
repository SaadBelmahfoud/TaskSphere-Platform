package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.port.out.ActivityLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE TRANSVERSAUX : ActivityLogService (Audit Log)
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — CROSS-CUTTING CONCERN :
 * ─────────────────────────────────────────
 * Ce service est appelé par PLUSIEURS autres services :
 * - TaskManager → logue les actions CRUD sur les tâches
 * - CommentManager → logue les actions CRUD sur les commentaires
 * - AdminController (IAM) → logue les changements de rôle
 *
 * C'est un "service d'application" (Application Service) qui coordonne
 * l'écriture dans l'audit log. Il n'a pas de logique métier complexe,
 * il se contente de créer un ActivityLog et de le sauvegarder.
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION — Propagation REQUIRES_NEW
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 * Ce service est appelé depuis DEUX contextes différents :
 *
 * 1. CommentManager.createComment() — appel DIRECT dans la même
 *    transaction que l'opération métier → propagation REQUIRED fonctionne.
 *
 * 2. TaskAuditEventListener.onAuditEvent() — appel APRÈS le commit
 *    de la transaction métier (@TransactionalEventListener AFTER_COMMIT).
 *    Dans ce contexte, il n'y a PLUS de transaction active.
 *    Avec propagation REQUIRED, Spring crée une NOUVELLE transaction.
 *    Mais dans certains cas (ex: thread async, contexte Spring manquant),
 *    la transaction peut ne PAS être créée correctement → l'INSERT
 *    dans activity_logs échoue silencieusement.
 *
 * SOLUTION : Propagation REQUIRES_NEW
 * ─────────────────────────────────────
 * Avec REQUIRES_NEW, Spring crée TOUJOURS une nouvelle transaction
 * indépendante, même s'il y en a déjà une active. Cela garantit que :
 * - L'audit log est persisté dans sa PROPRE transaction
 * - Si la transaction externe rollback, l'audit est DÉJÀ commité
 * - Si l'audit échoue, il n'impacte PAS la transaction externe
 * - Le comportement est IDENTIQUE que l'appel vienne d'un
 *   CommentManager ou d'un TaskAuditEventListener
 *
 * POURQUOI EST-CE CRITIQUE POUR L'ACTIVITY LOG ?
 * ──────────────────────────────────────────────────
 * Le listener @TransactionalEventListener(phase = AFTER_COMMIT) s'exécute
 * APRÈS le commit. À ce moment, la transaction métier est terminée.
 * Si on utilise REQUIRED, Spring tente de joindre une transaction inexistante
 * et peut silencieusement ignorer l'opération. Avec REQUIRES_NEW, on force
 * la création d'une nouvelle transaction dédiée à l'audit.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogPort activityLogPort;

    /**
     * Enregistre une action dans l'audit log.
     *
     * CORRECTION — Propagation REQUIRES_NEW :
     * Garantit que l'audit est TOUJOURS persisté, même quand appelé
     * depuis un @TransactionalEventListener(AFTER_COMMIT) où il n'y
     * a plus de transaction active.
     *
     * @param action      Le type d'action (de l'enum ActivityLog.Action)
     * @param description Description humaine de l'action
     * @param username    Email de l'utilisateur qui fait l'action
     * @param taskId      ID de la tâche concernée (null si hors contexte)
     * @param taskTitle   Titre de la tâche (denormalized pour affichage direct)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(ActivityLog.Action action, String description,
                    String username, String taskId, String taskTitle) {
        log.debug("ACTIVITY LOG : {} par {} — {}", action, username, description);
        ActivityLog activityLog = ActivityLog.create(action, description, username, taskId, taskTitle);
        activityLogPort.save(activityLog);
    }
}