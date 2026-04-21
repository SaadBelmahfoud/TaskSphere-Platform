package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Comment;
import com.tasksphere.core.port.out.CommentPersistencePort;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentManager {

    private final CommentPersistencePort commentPersistencePort;
    private final ActivityLogService activityLogService;

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
     * 3. Enregistrer l'action dans l'audit log
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

        // Enregistrer l'action dans l'audit log (même transaction)
        activityLogService.log(
                ActivityLog.Action.COMMENT_ADDED,
                "Commentaire ajouté sur '" + taskTitle + "'",
                username, taskId, taskTitle
        );

        return savedComment;
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR (PROPRIÉTAIRE UNIQUEMENT)
    // ═══════════════════════════════════════════════════════

    /**
     * Met à jour le contenu d'un commentaire.
     *
     * RBAC : Seul le PROPRIÉTAIRE (username === auteur) peut modifier.
     * Le contrôleur fait cette vérification AVANT d'appeler ce service.
     *
     * @param commentId L'ID du commentaire
     * @param newContent Le nouveau contenu
     * @param currentUsername L'email de l'utilisateur qui demande la modification
     * @return Optional avec le commentaire modifié, ou empty si non trouvé
     */
    @Transactional
    public Optional<Comment> updateComment(String commentId, String newContent, String currentUsername) {
        log.info("SERVICE : Modification du commentaire {} par {}", commentId, currentUsername);

        Optional<Comment> existing = commentPersistencePort.findById(commentId);
        if (existing.isEmpty()) return Optional.empty();

        Comment updated = existing.get().updateContent(newContent);
        Comment saved = commentPersistencePort.save(updated);

        // Log l'action
        activityLogService.log(
                ActivityLog.Action.COMMENT_UPDATED,
                "Commentaire modifié",
                currentUsername, saved.taskId(), null
        );

        return Optional.of(saved);
    }

    // ═══════════════════════════════════════════════════════
    // SUPPRESSION (PROPRIÉTAIRE OU ADMIN)
    // ═══════════════════════════════════════════════════════

    /**
     * Supprime un commentaire.
     *
     * RBAC : Le propriétaire ou un ADMIN peuvent supprimer.
     * Le contrôleur fait cette vérification AVANT d'appeler ce service.
     *
     * @param commentId L'ID du commentaire à supprimer
     * @param currentUsername L'email de l'utilisateur qui demande la suppression
     * @param currentRole Le rôle de l'utilisateur (pour vérifier ADMIN)
     * @return true si supprimé, false si non trouvé
     */
    @Transactional
    public boolean deleteComment(String commentId, String currentUsername, String currentRole) {
        log.info("SERVICE : Suppression du commentaire {} par {} (rôle: {})",
                commentId, currentUsername, currentRole);

        Optional<Comment> existing = commentPersistencePort.findById(commentId);
        if (existing.isEmpty()) return false;

        // Log AVANT la suppression (pour garder le taskId dans le log)
        Comment toDelete = existing.get();
        activityLogService.log(
                ActivityLog.Action.COMMENT_DELETED,
                "Commentaire supprimé",
                currentUsername, toDelete.taskId(), null
        );

        // Suppression physique (pas de soft delete pour les commentaires)
        commentPersistencePort.deleteById(commentId);
        return true;
    }
}