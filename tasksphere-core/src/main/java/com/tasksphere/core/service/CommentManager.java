package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Comment;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.port.out.CommentPersistencePort;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE MÉTIER : CommentManager
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Cœur de la logique métier pour les commentaires.
 * Même pattern architectural que TaskManager :
 * - Dans le domaine (package service)
 * - Ne connaît ni HTTP, ni JPA
 * - Travaille avec des objets domaine (Comment) et des ports
 *
 * RAPPEL — INJECTION DES DÉPENDANCES :
 * ────────────────────────────────────
 * @RequiredArgsConstructor (Lombok) génère un constructeur avec tous
 * les champs finaux. Spring injecte automatiquement les dépendances.
 * C'est la méthode d'injection RECOMMANDÉE par l'équipe Spring.
 *
 * NOUVEAU — DEUX PORTS INJECTÉS :
 * ────────────────────────────────
 * 1. CommentPersistencePort : port de sauvegarde des commentaires
 * 2. ActivityLogService : service transversal d'audit log
 *    (pour tracer chaque action sur les commentaires)
 *
 * RBAC POUR LES COMMENTAIRES :
 * ──────────────────────────────
 * - getCommentsByTaskId() : accessible à tous (la visibilité de la tâche
 *   est gérée par le contrôleur qui vérifie d'abord si l'utilisateur
 *   a le droit de voir la tâche via TaskManager)
 * - createComment() : accessible à tous les utilisateurs authentifiés
 * - updateComment() : seul le PROPRIÉTAIRE (username) peut modifier
 * - deleteComment() : le PROPRIÉTAIRE ou un ADMIN peuvent supprimer
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION ACTIVITY : Approche événementielle cohérente
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   CommentManager appelait activityLogService.log() directement dans
 *   la même transaction. Cela causait DEUX problèmes :
 *   1. Pas de notification WebSocket (seul TaskAuditEventListener
 *      envoie des notifications, et il ne reçoit QUE les TaskAuditEvent)
 *   2. taskTitle = null dans les entrées update/delete car le titre
 *      n'était pas récupéré
 *
 * APRÈS :
 *   CommentManager utilise maintenant EventPublisherPort (comme TaskManager)
 *   pour publier des TaskAuditEvent. Le TaskAuditEventListener :
 *   1. Enregistre l'audit log (post-commit, REQUIRES_NEW)
 *   2. Envoie la notification WebSocket temps réel
 *   3. taskTitle est récupéré via TaskPersistencePort
 *
 * DÉPENDANCES MODIFIÉES :
 * - ActivityLogService REMPLACÉ par EventPublisherPort
 * - TaskPersistencePort AJOUTÉ pour récupérer le titre de la tâche
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentManager {

    private final CommentPersistencePort commentPersistencePort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : EventPublisherPort remplace ActivityLogService
     * ═══════════════════════════════════════════════════════════════════
     * AVANT : ActivityLogService activityLogService (appel direct, même transaction)
     * APRÈS : EventPublisherPort eventPublisher (événement post-commit)
     *   → Cohérent avec TaskManager
     *   → Permet les notifications WebSocket via TaskAuditEventListener
     * ═══════════════════════════════════════════════════════════════════
     */
    private final EventPublisherPort eventPublisher;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : TaskPersistencePort pour taskTitle
     * ═══════════════════════════════════════════════════════════════════
     * Nécessaire pour récupérer le titre de la tâche lors des
     * opérations update/delete de commentaires (où le titre n'est
     * pas disponible directement). Corrige le bug taskTitle = null.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final TaskPersistencePort taskPersistencePort;

    // ═══════════════════════════════════════════════════════
    // LECTURE DES COMMENTAIRES
    // ═══════════════════════════════════════════════════════

    /**
     * Récupère tous les commentaires d'une tâche.
     *
     * SÉCURITÉ : Le contrôleur doit d'abord vérifier que l'utilisateur
     * a le droit de voir la tâche (via TaskManager.getTaskById).
     * Ce service ne vérifie PAS la visibilité de la tâche lui-même.
     *
     * @param taskId L'ID de la tâche
     * @return Liste des commentaires triés du plus récent au plus ancien
     */
    @Transactional(readOnly = true)
    public List<Comment> getCommentsByTaskId(String taskId) {
        log.debug("SERVICE : Récupération des commentaires pour la tâche {}", taskId);
        return commentPersistencePort.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE COMMENTAIRE
    // ═══════════════════════════════════════════════════════

    /**
     * Crée un nouveau commentaire sur une tâche.
     *
     * FLUX :
     * 1. Créer le Comment via Factory Method
     * 2. Sauvegarder via le port de persistance
     * 3. Publier un événement d'audit COMMENT_ADDED (post-commit)
     *
     * @param content  Le contenu du commentaire (validé par @NotBlank dans le DTO)
     * @param username L'email de l'auteur (extrait du JWT)
     * @param taskId   L'ID de la tâche
     * @param taskTitle Le titre de la tâche (pour le denormalized field de l'ActivityLog)
     * @return Le commentaire créé (avec ID généré)
     */
    @Transactional
    public Comment createComment(String content, String username, String taskId, String taskTitle) {
        log.info("SERVICE : Création d'un commentaire sur la tâche {} par {}", taskId, username);

        Comment comment = Comment.create(content, username, taskId);
        Comment savedComment = commentPersistencePort.save(comment);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : Événement au lieu d'appel direct
        // ═══════════════════════════════════════════════════════════════════
        // AVANT : activityLogService.log(COMMENT_ADDED, ..., taskId, taskTitle)
        //   → Pas de notification WebSocket
        // APRÈS : eventPublisher.publishAuditEvent(TaskAuditEvent)
        //   → Audit log + notification WebSocket via TaskAuditEventListener
        // ═══════════════════════════════════════════════════════════════════
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.COMMENT_ADDED,
                "Commentaire ajouté sur '" + taskTitle + "'",
                username, taskId, taskTitle
        ));

        return savedComment;
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR — CORRECTION B4 : Vérification de propriété
    // ═══════════════════════════════════════════════════════

    /**
     * Met à jour le contenu d'un commentaire.
     *
     * RBAC : Seul le PROPRIÉTAIRE (username === auteur) peut modifier.
     *
     * CORRECTION B4 : Ajout de la vérification de propriété
     * ──────────────────────────────────────────────────────
     * AVANT : Aucune vérification → n'importe quel utilisateur
     *   pouvait modifier n'importe quel commentaire
     *
     * APRÈS : Vérification que currentUsername === comment.username()
     *   → Si pas le propriétaire → Optional.empty() (404 au contrôleur)
     */
    @Transactional
    public Optional<Comment> updateComment(String commentId, String newContent, String currentUsername) {
        log.info("SERVICE : Modification du commentaire {} par {}", commentId, currentUsername);

        Optional<Comment> existing = commentPersistencePort.findById(commentId);
        if (existing.isEmpty()) return Optional.empty();

        // CORRECTION B4 : Vérification de propriété
        // ────────────────────────────────────────
        // On compare l'email de l'utilisateur courant avec celui de l'auteur
        // du commentaire. Si ce n'est pas le même → accès refusé.
        if (!existing.get().username().equals(currentUsername)) {
            log.warn("RBAC : User {} a tenté de modifier le commentaire {} appartenant à {}",
                    currentUsername, commentId, existing.get().username());
            return Optional.empty();  // Retourne "non trouvé" pour ne pas divulguer l'existence
        }

        Comment updated = existing.get().updateContent(newContent);
        Comment saved = commentPersistencePort.save(updated);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : taskTitle récupéré + événement
        // ═══════════════════════════════════════════════════════════════════
        // AVANT : activityLogService.log(COMMENT_UPDATED, ..., saved.taskId(), null)
        //   → taskTitle était null → affichage incomplet dans le Dashboard
        //   → Pas de notification WebSocket
        // APRÈS : On récupère le titre via TaskPersistencePort
        //   → taskTitle correct dans l'ActivityLog
        //   → Notification WebSocket envoyée
        // ═══════════════════════════════════════════════════════════════════
        String taskTitle = taskPersistencePort.findById(saved.taskId())
                .map(Task::title)
                .orElse("Tâche inconnue");

        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.COMMENT_UPDATED,
                "Commentaire modifié sur '" + taskTitle + "'",
                currentUsername, saved.taskId(), taskTitle
        ));

        return Optional.of(saved);
    }

    // ═══════════════════════════════════════════════════════
    // SUPPRESSION — CORRECTION B5 : Vérification propriétaire/admin
    // ═══════════════════════════════════════════════════════

    /**
     * Supprime un commentaire.
     *
     * RBAC : Le propriétaire ou un ADMIN peuvent supprimer.
     *
     * CORRECTION B5 : Ajout de la vérification de propriété/admin
     * ────────────────────────────────────────────────────────────
     * AVANT : Aucune vérification → n'importe quel utilisateur
     *   pouvait supprimer n'importe quel commentaire
     *
     * APRÈS : Vérification que currentUsername === comment.username()
     *   OU que currentRole === "ADMIN"
     */
    @Transactional
    public boolean deleteComment(String commentId, String currentUsername, String currentRole) {
        log.info("SERVICE : Suppression du commentaire {} par {} (rôle: {})",
                commentId, currentUsername, currentRole);

        Optional<Comment> existing = commentPersistencePort.findById(commentId);
        if (existing.isEmpty()) return false;

        Comment toDelete = existing.get();

        // CORRECTION B5 : Vérification de propriété ou rôle ADMIN
        // ──────────────────────────────────────────────────────────
        boolean isOwner = toDelete.username().equals(currentUsername);
        boolean isAdmin = "ADMIN".equals(currentRole);

        if (!isOwner && !isAdmin) {
            log.warn("RBAC : User {} (rôle: {}) a tenté de supprimer le commentaire {} appartenant à {}",
                    currentUsername, currentRole, commentId, toDelete.username());
            return false;  // Accès refusé
        }

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : taskTitle récupéré + événement
        // ═══════════════════════════════════════════════════════════════════
        // AVANT : activityLogService.log(COMMENT_DELETED, ..., toDelete.taskId(), null)
        //   → taskTitle était null → affichage incomplet dans le Dashboard
        //   → Pas de notification WebSocket
        // APRÈS : On récupère le titre via TaskPersistencePort
        //   → taskTitle correct dans l'ActivityLog
        //   → Notification WebSocket envoyée
        // ═══════════════════════════════════════════════════════════════════
        String taskTitle = taskPersistencePort.findById(toDelete.taskId())
                .map(Task::title)
                .orElse("Tâche inconnue");

        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.COMMENT_DELETED,
                "Commentaire supprimé sur '" + taskTitle + "'",
                currentUsername, toDelete.taskId(), taskTitle
        ));

        commentPersistencePort.deleteById(commentId);
        return true;
    }
}