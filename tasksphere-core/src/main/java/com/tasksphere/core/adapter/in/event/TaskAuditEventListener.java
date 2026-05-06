package com.tasksphere.core.adapter.in.event;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.service.ActivityLogService;
import com.tasksphere.core.service.NotificationService;
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
 * PHASE 3 — FEATURE 1 : Enrichissement avec notifications temps réel
 * ─────────────────────────────────────────────────────────────────────
 * En plus d'enregistrer l'audit log, ce listener envoie maintenant
 * des notifications WebSocket aux utilisateurs concernés.
 *
 * NOTIFICATION LOGIQUE :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Événement             │ Destinataire          │ Type           │
 * │  TASK_CREATED          │ Créateur (confirmation)│ INFO           │
 * │  TASK_STATUS_CHANGED   │ Créateur + Assignataire│ INFO          │
 * │  TASK_ASSIGNED         │ Assignataire          │ WARNING        │
 * │  TASK_UNASSIGNED       │ Ancien assignataire   │ WARNING        │
 * │  TASK_UPDATED          │ Créateur + Assignataire│ INFO          │
 * │  TASK_DELETED          │ Créateur + Assignataire│ WARNING       │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * NOTE : Dans cette version, on envoie la notification à l'acteur
 * lui-même (confirmation d'action). Dans une version future, on
 * pourrait ajouter un port NotificationPort pour récupérer le
 * créateur et l'assignataire d'une tâche et leur envoyer des
 * notifications ciblées.
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
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION — Logging d'erreur amélioré
 * ═══════════════════════════════════════════════════════════════════
 * PROBLÈME PRÉCÉDENT :
 * Si l'audit échouait (exception), le catch ne loguait qu'un WARN
 * avec le message court (e.getMessage()). Cela masquait la stack trace
 * et rendait le diagnostic impossible en production.
 *
 * APRÈS :
 * On logue l'exception COMPLÈTE (stack trace incluse) au niveau ERROR
 * pour permettre un diagnostic rapide en production. L'audit reste
 * un "best effort" — l'exception n'est JAMAIS propagée.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAuditEventListener {

    private final ActivityLogService activityLogService;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 1 : Injection du NotificationService
     * ═══════════════════════════════════════════════════════════════════
     * NotificationService utilise SimpMessagingTemplate pour envoyer
     * des messages WebSocket. L'injection se fait via le constructeur
     * (RequiredArgsConstructor de Lombok).
     * ═══════════════════════════════════════════════════════════════════
     */
    private final NotificationService notificationService;

    /**
     * Écoute les événements d'audit APRÈS le commit de la transaction métier.
     *
     * PHASE 3 — FEATURE 1 : Enrichi avec l'envoi de notifications temps réel.
     * En plus de l'audit log, on envoie une notification WebSocket à l'acteur.
     *
     * CORRECTION — Logging d'erreur amélioré :
     * On logue l'exception COMPLÈTE (niveau ERROR) pour le diagnostic.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(TaskAuditEvent event) {
        try {
            log.debug("AUDIT LISTENER : Enregistrement post-commit — action={}, actor={}",
                    event.action(), event.username());

            // 1. Enregistrer l'audit log (comportement existant)
            activityLogService.log(
                    event.action(),
                    event.description(),
                    event.username(),
                    event.taskId(),
                    event.taskTitle()
            );

            log.debug("AUDIT LISTENER : Audit enregistré avec succès — action={}", event.action());

            // ═══════════════════════════════════════════════════════════════════
            // PHASE 3 — FEATURE 1 : Envoi de notification temps réel
            // ═══════════════════════════════════════════════════════════════════
            // Après l'audit, on envoie une notification WebSocket à l'utilisateur.
            // Le type de notification dépend de l'action :
            // - TASK_ASSIGNED → WARNING (attention, une tâche vous est assignée)
            // - TASK_DELETED → WARNING (attention, une tâche a été supprimée)
            // - Les autres → INFO (information générale)
            //
            // NOTE : On envoie à l'acteur (username) pour confirmation d'action.
            // Dans une version future, on pourrait aussi notifier l'assignataire
            // et le créateur de la tâche (nécessiterait un port pour récupérer
            // ces informations depuis la tâche).
            // ═══════════════════════════════════════════════════════════════════
            Notification.NotificationType type = determineNotificationType(event.action());
            Notification notification = Notification.create(
                    type,
                    formatTitle(event.action()),
                    event.description(),
                    event.username(),    // Destinataire = acteur (confirmation)
                    event.taskId(),
                    event.taskTitle()
            );
            notificationService.notifyUser(event.username(), notification);

        } catch (Exception e) {
            // CORRECTION — Logging d'erreur amélioré :
            // L'audit est un "best effort" : on loggue l'erreur COMPLÈTE
            // (stack trace incluse) mais on ne propage PAS l'exception
            // car l'opération métier est déjà commitée.
            log.error("AUDIT LISTENER : ÉCHEC de l'enregistrement post-commit — " +
                            "action={}, actor={} — L'audit n'a PAS été enregistré !",
                    event.action(), event.username(), e);
        }
    }

    /**
     * Détermine le type de notification selon l'action.
     *
     * LOGIQUE :
     * - TASK_ASSIGNED → WARNING : l'utilisateur doit être alerté d'une assignation
     * - TASK_DELETED → WARNING : action destructive, l'utilisateur doit être informé
     * - Les autres → INFO : information générale
     */
    private Notification.NotificationType determineNotificationType(ActivityLog.Action action) {
        return switch (action) {
            case TASK_ASSIGNED -> Notification.NotificationType.WARNING;
            case TASK_DELETED -> Notification.NotificationType.WARNING;
            default -> Notification.NotificationType.INFO;
        };
    }

    /**
     * Formate le titre de la notification selon l'action.
     *
     * Le titre est court et lisible pour l'affichage dans un toast/badge.
     */
    private String formatTitle(ActivityLog.Action action) {
        return switch (action) {
            case TASK_CREATED -> "Nouvelle tâche créée";
            case TASK_UPDATED -> "Tâche modifiée";
            case TASK_STATUS_CHANGED -> "Statut modifié";
            case TASK_ASSIGNED -> "Tâche assignée";
            case TASK_UNASSIGNED -> "Assignation retirée";
            case TASK_DELETED -> "Tâche supprimée";
            case COMMENT_ADDED -> "Commentaire ajouté";
            case COMMENT_UPDATED -> "Commentaire modifié";
            case COMMENT_DELETED -> "Commentaire supprimé";
            case USER_ROLE_CHANGED -> "Rôle modifié";
            case USER_TOGGLED -> "Statut utilisateur modifié";
        };
    }
}