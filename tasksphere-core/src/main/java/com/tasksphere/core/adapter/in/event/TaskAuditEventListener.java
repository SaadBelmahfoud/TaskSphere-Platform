package com.tasksphere.core.adapter.in.event;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.service.ActivityLogService;
import com.tasksphere.core.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION N1 : Routage des notifications vers les affectés
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 *   Les notifications étaient envoyées UNIQUEMENT à l'acteur
 *   (celui qui fait l'action). Par exemple :
 *   - Un MANAGER assigne une tâche → le MANAGER reçoit la notification
 *   - L'assigné ne sait JAMAIS qu'on lui a assigné une tâche !
 *
 * SOLUTION :
 *   Envoyer les notifications aux utilisateurs AFFECTÉS par l'action :
 *
 *   NOTIFICATION LOGIQUE (CORRIGÉE) :
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Événement             │ Destinataires                            │
 *   ├────────────────────────┼─────────────────────────────────────────┤
 *   │  TASK_CREATED          │ Acteur (confirmation INFO)              │
 *   │  TASK_STATUS_CHANGED   │ Créateur + Assignataire (INFO)         │
 *   │  TASK_ASSIGNED         │ Assignataire (WARNING) + Acteur (INFO) │
 *   │  TASK_UNASSIGNED       │ Ancien assignataire (WARNING)          │
 *   │  TASK_UPDATED          │ Créateur + Assignataire (INFO)         │
 *   │  TASK_DELETED          │ Créateur + Assignataire (WARNING)      │
 *   │  COMMENT_ADDED         │ Créateur + Assignataire - Acteur (INFO)│
 *   │  COMMENT_UPDATED       │ Créateur + Assignataire - Acteur (INFO)│
 *   │  COMMENT_DELETED       │ Créateur + Assignataire - Acteur (WARN)│
 *   │  ATTACHMENT_UPLOADED   │ Créateur + Assignataire - Acteur (INFO)│
 *   │  ATTACHMENT_DELETED    │ Créateur + Assignataire - Acteur (WARN)│
 *   │  TAG_ADDED_TO_TASK     │ Créateur + Assignataire - Acteur (INFO)│
 *   │  TAG_REMOVED_FROM_TASK │ Créateur + Assignataire - Acteur (INFO)│
 *   │  TAG_CREATED           │ Acteur (confirmation INFO)             │
 *   │  TAG_DELETED           │ Acteur (confirmation WARNING)          │
 *   │  USER_ROLE_CHANGED     │ Acteur (confirmation WARNING)          │
 *   │  USER_TOGGLED          │ Acteur (confirmation WARNING)          │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   PRINCIPE — NOTIFICATION CIBLÉE :
 *   - L'ACTEUR reçoit une confirmation d'action (INFO)
 *   - Les AFFECTÉS (créateur, assigné) reçoivent une notification
 *     d'information ou d'alerte selon l'action
 *   - On EXCLUT l'acteur de la liste des affectés pour ne pas
 *     envoyer de doublons (l'acteur reçoit sa propre confirmation)
 *
 *   IMPLÉMENTATION :
 *   1. Récupérer la tâche via TaskPersistencePort (si taskId présent)
 *   2. Construire la liste des destinataires affectés
 *   3. Envoyer une notification INFO à l'acteur (confirmation)
 *   4. Envoyer une notification INFO/WARNING aux affectés
 * ═══════════════════════════════════════════════════════════════════
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
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — actorUsername ajouté à Notification.create()
 * ═══════════════════════════════════════════════════════════════════
 * PROBLÈME :
 * Notification.create() attend maintenant 7 paramètres car le champ
 * actorUsername a été ajouté au domaine Notification. L'appel avec
 * seulement 6 paramètres causait une erreur de compilation :
 *   required: NotificationType, String, String, String, String, String, String
 *   found:    NotificationType, String, String, String, String, String
 *
 * SOLUTION :
 * Ajout du 7ème paramètre event.username() qui correspond à l'acteur
 * qui a déclenché l'action.
 * ═══════════════════════════════════════════════════════════════════
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION ACTIVITY : Support des nouvelles actions
 * ═══════════════════════════════════════════════════════════════════
 * Ajout des cas pour ATTACHMENT_UPLOADED, ATTACHMENT_DELETED,
 * TAG_CREATED, TAG_DELETED, TAG_ADDED_TO_TASK, TAG_REMOVED_FROM_TASK
 * dans formatTitle() et determineNotificationType().
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
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION N1 : Injection du TaskPersistencePort
     * ═══════════════════════════════════════════════════════════════════
     * Permet de récupérer le créateur (userId) et l'assignataire
     * (assigneeId) d'une tâche pour leur envoyer des notifications
     * ciblées au lieu de n'envoyer qu'à l'acteur.
     *
     * AVANT : notificationService.notifyUser(event.username(), ...)
     * → L'acteur reçoit la notification, les affectés ne savent rien.
     *
     * APRÈS : notificationService.notifyUser(créateur/assignataire, ...)
     * → Les affectés sont informés des changements qui les concernent.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final TaskPersistencePort taskPersistencePort;

    /**
     * Écoute les événements d'audit APRÈS le commit de la transaction métier.
     *
     * PHASE 3 — FEATURE 1 : Enrichi avec l'envoi de notifications temps réel.
     * PHASE 3 — CORRECTION N1 : Notifications envoyées aux affectés (créateur + assigné).
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
            // PHASE 3 — CORRECTION N1 : Notifications ciblées
            // ═══════════════════════════════════════════════════════════════════
            // Au lieu d'envoyer UNIQUEMENT à l'acteur, on envoie aux :
            // 1. Acteur → confirmation d'action (INFO)
            // 2. Créateur de la tâche → information sur les changements (INFO)
            // 3. Assignataire → alerte sur les changements le concernant (WARNING)
            //
            // On EXCLUT l'acteur de la liste des affectés pour éviter les doublons.
            //
            // POUR LES ACTIONS SANS TÂCHE (TAG_CREATED, TAG_DELETED, etc.) :
            // On envoie uniquement à l'acteur (confirmation).
            // ═══════════════════════════════════════════════════════════════════
            sendNotifications(event);

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
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION N1 : Logique de routage des notifications
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE — TRIPLE NOTIFICATION :
     * 1. L'ACTEUR reçoit une confirmation d'action (INFO)
     * 2. Le CRÉATEUR de la tâche reçoit une notification d'information
     * 3. L'ASSIGNATAIRE reçoit une notification d'alerte (si action le concernant)
     *
     * EXCLUSION DE L'ACTEUR :
     * Si l'acteur EST le créateur ou l'assignataire, on ne lui envoie
     * qu'une seule notification (sa confirmation d'action). Pas de doublon.
     *
     * EXCEPTION — COMMENTAIRES ET PJ :
     * Pour COMMENT_ADDED, COMMENT_UPDATED, COMMENT_DELETED,
     * ATTACHMENT_UPLOADED, ATTACHMENT_DELETED, TAG_ADDED_TO_TASK,
     * TAG_REMOVED_FROM_TASK :
     * On NOTIFIE le créateur et l'assignataire MAIS PAS l'acteur
     * (celui qui a fait l'action n'a pas besoin de confirmation pour
     * ces actions mineures). On envoie plutôt aux personnes concernées
     * par la tâche.
     * ═══════════════════════════════════════════════════════════════════
     */
    private void sendNotifications(TaskAuditEvent event) {
        String actor = event.username();
        Notification.NotificationType actorType = determineNotificationType(event.action());
        String title = formatTitle(event.action());

        // ── Actions SANS tâche (globales) : uniquement à l'acteur ──
        if (event.taskId() == null) {
            Notification actorNotification = Notification.create(
                    actorType, title, event.description(),
                    actor, null, null, actor
            );
            notificationService.notifyUser(actor, actorNotification);
            return;
        }

        // ── Actions AVEC tâche : récupérer créateur + assignataire ──
        Optional<Task> taskOpt = taskPersistencePort.findById(event.taskId());
        if (taskOpt.isEmpty()) {
            // Tâche supprimée (soft delete) ou introuvable
            // On envoie uniquement à l'acteur comme fallback
            Notification fallback = Notification.create(
                    actorType, title, event.description(),
                    actor, event.taskId(), event.taskTitle(), actor
            );
            notificationService.notifyUser(actor, fallback);
            return;
        }

        Task task = taskOpt.get();
        String creator = task.userId();
        String assignee = task.assigneeId();

        // Construire la liste des destinataires affectés (sans doublon, sans l'acteur)
        Set<String> affectedUsers = new LinkedHashSet<>();
        if (creator != null && !creator.equals(actor)) {
            affectedUsers.add(creator);
        }
        if (assignee != null && !assignee.equals(actor) && !assignee.equals(creator)) {
            affectedUsers.add(assignee);
        }

        // ── Déterminer si l'acteur reçoit une confirmation ──
        // Pour les actions "mineures" (commentaires, PJ, tags), on ne notifie
        // PAS l'acteur — on notifie uniquement les affectés.
        boolean notifyActor = shouldNotifyActor(event.action());

        if (notifyActor) {
            // Envoyer confirmation à l'acteur
            Notification actorNotification = Notification.create(
                    Notification.NotificationType.INFO, title, event.description(),
                    actor, event.taskId(), event.taskTitle(), actor
            );
            notificationService.notifyUser(actor, actorNotification);
        }

        // Envoyer notification aux affectés
        Notification.NotificationType affectedType = determineAffectedType(event.action());
        for (String affectedUser : affectedUsers) {
            Notification affectedNotification = Notification.create(
                    affectedType, title, event.description(),
                    affectedUser, event.taskId(), event.taskTitle(), actor
            );
            notificationService.notifyUser(affectedUser, affectedNotification);
        }
    }

    /**
     * Détermine si l'acteur doit recevoir une confirmation d'action.
     *
     * LOGIQUE :
     * - Pour les actions "majeures" (création, modification, suppression,
     *   changement de statut, assignation) : OUI → l'acteur reçoit un INFO
     * - Pour les actions "mineures" (commentaires, PJ, tags sur tâche) :
     *   NON → seul les affectés sont notifiés
     */
    private boolean shouldNotifyActor(ActivityLog.Action action) {
        return switch (action) {
            case TASK_CREATED, TASK_UPDATED, TASK_STATUS_CHANGED,
                 TASK_ASSIGNED, TASK_UNASSIGNED, TASK_DELETED,
                 TAG_CREATED, TAG_DELETED,
                 USER_ROLE_CHANGED, USER_TOGGLED -> true;
            case COMMENT_ADDED, COMMENT_UPDATED, COMMENT_DELETED,
                 ATTACHMENT_UPLOADED, ATTACHMENT_DELETED,
                 TAG_ADDED_TO_TASK, TAG_REMOVED_FROM_TASK -> false;
        };
    }

    /**
     * Détermine le type de notification pour les utilisateurs affectés.
     *
     * LOGIQUE :
     * - TASK_ASSIGNED → WARNING : l'assignataire doit être alerté
     * - TASK_DELETED → WARNING : action destructive
     * - TASK_UNASSIGNED → WARNING : l'ancien assignataire doit être alerté
     * - COMMENT_DELETED → WARNING : action destructive
     * - ATTACHMENT_DELETED → WARNING : action destructive
     * - Les autres → INFO : information générale
     */
    private Notification.NotificationType determineAffectedType(ActivityLog.Action action) {
        return switch (action) {
            case TASK_ASSIGNED, TASK_UNASSIGNED, TASK_DELETED,
                 COMMENT_DELETED, ATTACHMENT_DELETED -> Notification.NotificationType.WARNING;
            default -> Notification.NotificationType.INFO;
        };
    }

    /**
     * Détermine le type de notification selon l'action (pour l'acteur).
     *
     * LOGIQUE :
     * - TASK_ASSIGNED → WARNING : l'utilisateur doit être alerté d'une assignation
     * - TASK_DELETED → WARNING : action destructive, l'utilisateur doit être informé
     * - ATTACHMENT_DELETED → WARNING : action destructive (fichier supprimé)
     * - TAG_DELETED → WARNING : action destructive (tag supprimé)
     * - USER_ROLE_CHANGED → WARNING : action sensible sur un utilisateur
     * - USER_TOGGLED → WARNING : action sensible sur un utilisateur
     * - Les autres → INFO : information générale
     */
    private Notification.NotificationType determineNotificationType(ActivityLog.Action action) {
        return switch (action) {
            case TASK_ASSIGNED -> Notification.NotificationType.WARNING;
            case TASK_DELETED -> Notification.NotificationType.WARNING;
            case ATTACHMENT_DELETED -> Notification.NotificationType.WARNING;
            case TAG_DELETED -> Notification.NotificationType.WARNING;
            case USER_ROLE_CHANGED -> Notification.NotificationType.WARNING;
            case USER_TOGGLED -> Notification.NotificationType.WARNING;
            default -> Notification.NotificationType.INFO;
        };
    }

    /**
     * Formate le titre de la notification selon l'action.
     *
     * Le titre est court et lisible pour l'affichage dans un toast/badge.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : Titres pour les nouvelles actions
     * ═══════════════════════════════════════════════════════════════════
     * Ajout des cas pour les pièces jointes et les tags.
     * ═══════════════════════════════════════════════════════════════════
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
            // PHASE 3 — CORRECTION ACTIVITY : Pièces jointes
            case ATTACHMENT_UPLOADED -> "Pièce jointe ajoutée";
            case ATTACHMENT_DELETED -> "Pièce jointe supprimée";
            // PHASE 3 — CORRECTION ACTIVITY : Tags
            case TAG_CREATED -> "Tag créé";
            case TAG_DELETED -> "Tag supprimé";
            case TAG_ADDED_TO_TASK -> "Tag ajouté à la tâche";
            case TAG_REMOVED_FROM_TASK -> "Tag retiré de la tâche";
            // Administration
            case USER_ROLE_CHANGED -> "Rôle modifié";
            case USER_TOGGLED -> "Statut utilisateur modifié";
        };
    }
}