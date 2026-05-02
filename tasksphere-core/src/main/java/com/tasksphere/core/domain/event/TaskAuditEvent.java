package com.tasksphere.core.domain.event;

import com.tasksphere.core.domain.ActivityLog;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ÉVÉNEMENT DOMAINE : TaskAuditEvent
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 2 — TÂCHE 4 : Événement pour audit post-commit
 * ─────────────────────────────────────────────────────────
 *
 * PRINCIPE — EVENT-DRIVEN AUDIT :
 * ────────────────────────────────
 * Au lieu d'appeler directement ActivityLogService.log() dans
 * la même transaction que l'opération métier, on publie un événement.
 * L'audit est alors exécuté APRÈS le commit de la transaction métier.
 *
 * FLUX AVANT (audit synchrone dans la même transaction) :
 * ┌──────────────────────────────────────────────────────────────┐
 * │ @Transactional                                               │
 * │ TaskManager.createTask() {                                   │
 * │   1. taskPersistencePort.save(task)  → INSERT task          │
 * │   2. activityLogService.log(...)     → INSERT audit log     │
 * │   3. COMMIT (les DEUX ou AUCUNE)                             │
 * │ }                                                            │
 * │                                                              │
 * │ PROBLÈME :                                                   │
 * │ - Si l'audit échoue → try-catch le masque → pas de log !   │
 * │ - Si l'audit lève une RuntimeException non attrapée →      │
 * │   la transaction est rollback → la tâche n'est PAS créée !  │
 * │ - L'audit ne DEVRAIT JAMAIS faire échouer l'opération       │
 * └──────────────────────────────────────────────────────────────┘
 *
 * FLUX APRÈS (audit asynchrone post-commit) :
 * ┌──────────────────────────────────────────────────────────────┐
 * │ @Transactional                                               │
 * │ TaskManager.createTask() {                                   │
 * │   1. taskPersistencePort.save(task)  → INSERT task          │
 * │   2. eventPublisher.publishAuditEvent(...)                   │
 * │   3. COMMIT (seule l'opération métier)                       │
 * │ }                                                            │
 * │                                                              │
 * │ APRÈS LE COMMIT :                                            │
 * │ @TransactionalEventListener(phase = AFTER_COMMIT)            │
 * │ AuditEventListener.onAuditEvent() {                          │
 * │   → activityLogService.log(...)  → INSERT audit log          │
 * │   → NOUVELLE transaction indépendante                        │
 * │   → Si l'audit échoue → l'opération métier est DÉJÀ commit │
 * │ }                                                            │
 * └──────────────────────────────────────────────────────────────┘
 *
 * POURQUOI @TransactionalEventListener ET PAS @EventListener ?
 * ──────────────────────────────────────────────────────────
 * @EventListener : exécuté DANS la même transaction.
 *   → Si l'audit échoue → rollback de l'opération métier !
 *   → C'est le MÊME problème qu'avant.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) :
 *   → Exécuté APRÈS le commit de la transaction métier.
 *   → L'opération est déjà persistée en base.
 *   → L'audit fonctionne dans sa propre transaction.
 *   → Si l'audit échoue → l'opération n'est PAS impactée.
 *
 * POURQUOI PAS @Transactional(propagation = REQUIRES_NEW) ?
 * ────────────────────────────────────────────────────────
 * REQUIRES_NEW suspend la transaction en cours et en crée une nouvelle.
 * PROBLÈME : si l'audit échoue, la transaction extérieure reprend
 * et peut continuer. Mais le code est plus complexe et moins clair
 * que l'approche événementielle. De plus, avec REQUIRES_NEW,
 * l'audit est exécuté AVANT le commit de l'opération → si l'opération
 * échoue après l'audit, on a un log pour une action qui n'a PAS eu lieu.
 *
 * Avec AFTER_COMMIT : l'audit n'est exécuté QUE si l'opération a
 * RÉUSSI et est COMMITÉE. Pas de log fantôme.
 *
 * RECORD POUR IMMUTABILITÉ :
 * ──────────────────────────
 * Cet événement est un record (immutable). Une fois publié,
 * ses données ne peuvent pas être modifiées par les listeners.
 * C'est une garantie de cohérence : chaque listener voit les
 * MÊMES données que celles envoyées par le publisher.
 */
public record TaskAuditEvent(
        /** Le type d'action auditée (de l'enum ActivityLog.Action) */
        ActivityLog.Action action,

        /** Description humaine de l'action */
        String description,

        /** Email de l'utilisateur qui a fait l'action */
        String username,

        /** ID de la tâche concernée (null si hors contexte tâche) */
        String taskId,

        /** Titre de la tâche (denormalized pour affichage direct) */
        String taskTitle
) {}