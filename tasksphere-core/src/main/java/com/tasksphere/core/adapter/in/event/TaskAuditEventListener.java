package com.tasksphere.core.adapter.in.event;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : TaskAuditEventListener
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 2 — TÂCHE 4 : Listener d'audit post-commit
 * ─────────────────────────────────────────────────────
 *
 * RÔLE : Écouter les événements TaskAuditEvent publiés par TaskManager
 * et les enregistrer dans l'audit log APRÈS le commit de la transaction.
 *
 * PRINCIPE @TransactionalEventListener :
 * ───────────────────────────────────────
 * Spring fournit @TransactionalEventListener qui permet de réagir
 * à un événement à un point précis du cycle de vie de la transaction :
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ Phase             │ Quand le listener est appelé                │
 * ├───────────────────┼─────────────────────────────────────────────┤
 * │ BEFORE_COMMIT     │ AVANT le commit (dans la même transaction) │
 * │ AFTER_COMMIT      │ APRÈS le commit (transaction terminée) ✅  │
 * │ AFTER_ROLLBACK    │ APRÈS un rollback                          │
 * │ AFTER_COMPLETION  │ APRÈS commit OU rollback                   │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * NOTRE CHOIX : AFTER_COMMIT
 * → L'audit n'est enregistré QUE si l'opération métier a réussi.
 * → Si l'opération échoue (rollback), l'audit n'est PAS enregistré.
 * → C'est le comportement souhaité : on ne log que les actions EFFECTIVES.
 *
 * NOUVELLE TRANSACTION IMPLICITE :
 * ────────────────────────────────
 * Quand le listener est appelé après le commit, la transaction
 * métier est TERMINÉE. Si ActivityLogService.log() est annoté
 * @Transactional, Spring ouvrira une NOUVELLE transaction pour
 * l'audit. C'est la transaction "par défaut" (REQUIRED) qui crée
 * une nouvelle transaction si aucune n'existe.
 *
 * SCHÉMA :
 * ┌──────────────────────────────────────────────────────────┐
 * │ Transaction 1 (métier) :                                 │
 * │   1. INSERT task                                         │
 * │   2. PUBLISH TaskAuditEvent                              │
 * │   3. COMMIT                                              │
 * │                                                          │
 * │ Transaction 2 (audit) — AFTER_COMMIT :                   │
 * │   4. @TransactionalEventListener déclenché               │
 * │   5. INSERT activity_log                                 │
 * │   6. COMMIT (indépendant de la transaction 1)            │
 * └──────────────────────────────────────────────────────────┘
 *
 * SÉCURITÉ : Si l'audit échoue (exception), l'opération métier
 * est DÉJÀ commitée → pas de rollback. L'erreur est loguée en
 * WARN mais ne provoque PAS d'échec côté client.
 *
 * FALLBACK SI L'ÉVÉNEMENT EST MANQUÉ :
 * ────────────────────────────────────
 * Si l'application crash entre le commit de l'opération et
 * l'exécution du listener AFTER_COMMIT, l'audit sera perdu.
 * C'est un compromis acceptable : l'audit est un "best effort"
 * (meilleur effort). La priorité est la fiabilité de l'opération.
 *
 * POURQUOI CETTE CLASSE EST DANS adapter/in/event ?
 * ─────────────────────────────────────────────────
 * C'est un adaptateur d'entrée (in) car il réagit à un événement
 * externe (TaskAuditEvent) et appelle le service (ActivityLogService).
 * C'est le même pattern que TaskCreatedEventListener qui est
 * aussi dans adapter/in/event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAuditEventListener {

    private final ActivityLogService activityLogService;

    /**
     * Écoute les événements d'audit APRÈS le commit de la transaction métier.
     *
     * @param event L'événement d'audit contenant les détails de l'action
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(TaskAuditEvent event) {
        try {
            log.debug("AUDIT LISTENER : Enregistrement post-commit — action={}, actor={}",
                    event.action(), event.username());

            activityLogService.log(
                    event.action(),
                    event.description(),
                    event.username(),
                    event.taskId(),
                    event.taskTitle()
            );

            log.debug("AUDIT LISTENER : Audit enregistré avec succès — action={}", event.action());

        } catch (Exception e) {
            // L'audit est un "best effort" : on loggue l'erreur mais on ne
            // propage PAS l'exception car l'opération métier est déjà commitée.
            log.warn("AUDIT LISTENER : Échec de l'enregistrement post-commit — " +
                            "action={}, actor={} — Cause : {}",
                    event.action(), event.username(), e.getMessage());
        }
    }
}