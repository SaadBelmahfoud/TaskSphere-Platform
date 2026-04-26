package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Comment;
import com.tasksphere.core.port.out.CommentPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : CommentManager (NOUVEAU)
 * ═══════════════════════════════════════════════════════════════════
 *
 * CE FICHIER EST ENTIEREMENT NOUVEAU — Il n'existait pas avant.
 *
 * COUVERTURE :
 * - createComment() : vérifie création + audit
 * - getCommentsByTaskId() : vérifie l'appel au port
 * - updateComment() : vérifie RBAC (seul le propriétaire peut modifier)
 * - deleteComment() : vérifie RBAC (propriétaire OU admin)
 *
 * PRINCIPE DE TEST RBAC POUR LES COMMENTAIRES :
 * ──────────────────────────────────────────────
 * Le modèle de sécurité des commentaires est :
 * - Lecture : tout utilisateur authentifié (pas de vérification ici)
 * - Création : tout utilisateur authentifié
 * - Modification : seul le PROPRIÉTAIRE du commentaire (username match)
 * - Suppression : PROPRIÉTAIRE ou ADMIN
 *
 * Ce modèle est standard dans les applications collaboratives :
 * → Vous pouvez modifier/supprimer VOS commentaires
 * → Un admin peut supprimer n'importe quel commentaire (modération)
 * → Personne ne peut modifier le commentaire d'un autre
 */
@ExtendWith(MockitoExtension.class)
class CommentManagerTest {

    @Mock
    private CommentPersistencePort commentPersistencePort;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private CommentManager commentManager;

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE COMMENTAIRE
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("createComment()")
    class CreateComment {

        @Test
        @DisplayName("Devrait créer un commentaire et enregistrer l'audit")
        void shouldCreateAndAudit() {
            Comment comment = Comment.create("Mon commentaire", "user@test.com", "task-1");
            when(commentPersistencePort.save(any(Comment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Comment result = commentManager.createComment(
                    "Mon commentaire", "user@test.com", "task-1", "Ma tâche");

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("Mon commentaire");
            assertThat(result.username()).isEqualTo("user@test.com");
            assertThat(result.taskId()).isEqualTo("task-1");

            // Vérifier l'audit
            verify(activityLogService, times(1)).log(
                    eq(ActivityLog.Action.COMMENT_ADDED),
                    contains("Ma tâche"),
                    eq("user@test.com"),
                    eq("task-1"),
                    eq("Ma tâche"));
        }
    }

    // ═══════════════════════════════════════════════════════
    // LECTURE
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCommentsByTaskId()")
    class GetComments {

        @Test
        @DisplayName("Devrait retourner les commentaires triés par date décroissante")
        void shouldReturnCommentsSortedByDateDesc() {
            Comment c1 = Comment.create("Commentaire 1", "user1@test.com", "task-1");
            Comment c2 = Comment.create("Commentaire 2", "user2@test.com", "task-1");
            when(commentPersistencePort.findByTaskIdOrderByCreatedAtDesc("task-1"))
                    .thenReturn(List.of(c2, c1));

            List<Comment> result = commentManager.getCommentsByTaskId("task-1");

            assertThat(result).hasSize(2);
            verify(commentPersistencePort).findByTaskIdOrderByCreatedAtDesc("task-1");
        }
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR AVEC RBAC (CORRECTION B4)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateComment()")
    class UpdateComment {

        @Test
        @DisplayName("Propriétaire → peut modifier")
        void owner_shouldUpdate() {
            Comment existing = Comment.create("Ancien", "user@test.com", "task-1");
            when(commentPersistencePort.findById("comment-1"))
                    .thenReturn(Optional.of(existing));
            when(commentPersistencePort.save(any(Comment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Comment> result = commentManager.updateComment(
                    "comment-1", "Nouveau contenu", "user@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().content()).isEqualTo("Nouveau contenu");
        }

        @Test
        @DisplayName("Non-propriétaire → ne peut PAS modifier (CORRECTION B4)")
        void nonOwner_shouldNotUpdate() {
            // CORRECTION B4 : Vérification de propriété pour update
            Comment existing = Comment.create("Commentaire", "owner@test.com", "task-1");
            when(commentPersistencePort.findById("comment-1"))
                    .thenReturn(Optional.of(existing));

            Optional<Comment> result = commentManager.updateComment(
                    "comment-1", "Hack!", "hacker@test.com");

            assertThat(result).isEmpty();
            verify(commentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Commentaire inexistant → retourne empty")
        void nonExistent_shouldReturnEmpty() {
            when(commentPersistencePort.findById("comment-999"))
                    .thenReturn(Optional.empty());

            Optional<Comment> result = commentManager.updateComment(
                    "comment-999", "Nouveau", "user@test.com");

            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SUPPRESSION AVEC RBAC (CORRECTION B5)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteComment()")
    class DeleteComment {

        @Test
        @DisplayName("Propriétaire → peut supprimer")
        void owner_shouldDelete() {
            Comment existing = Comment.create("Commentaire", "user@test.com", "task-1");
            when(commentPersistencePort.findById("comment-1"))
                    .thenReturn(Optional.of(existing));

            boolean result = commentManager.deleteComment(
                    "comment-1", "user@test.com", "USER");

            assertThat(result).isTrue();
            verify(commentPersistencePort).deleteById("comment-1");
        }

        @Test
        @DisplayName("ADMIN → peut supprimer n'importe quel commentaire")
        void admin_shouldDeleteAnyComment() {
            Comment existing = Comment.create("Commentaire", "other@test.com", "task-1");
            when(commentPersistencePort.findById("comment-1"))
                    .thenReturn(Optional.of(existing));

            boolean result = commentManager.deleteComment(
                    "comment-1", "admin@test.com", "ADMIN");

            assertThat(result).isTrue();
            verify(commentPersistencePort).deleteById("comment-1");
        }

        @Test
        @DisplayName("Non-propriétaire non-ADMIN → ne peut PAS supprimer (CORRECTION B5)")
        void nonOwnerNonAdmin_shouldNotDelete() {
            // CORRECTION B5 : Vérification propriétaire/admin pour delete
            Comment existing = Comment.create("Commentaire", "owner@test.com", "task-1");
            when(commentPersistencePort.findById("comment-1"))
                    .thenReturn(Optional.of(existing));

            boolean result = commentManager.deleteComment(
                    "comment-1", "stranger@test.com", "USER");

            assertThat(result).isFalse();
            verify(commentPersistencePort, never()).deleteById(any());
        }

        @Test
        @DisplayName("Commentaire inexistant → retourne false")
        void nonExistent_shouldReturnFalse() {
            when(commentPersistencePort.findById("comment-999"))
                    .thenReturn(Optional.empty());

            boolean result = commentManager.deleteComment(
                    "comment-999", "user@test.com", "USER");

            assertThat(result).isFalse();
        }
    }
}