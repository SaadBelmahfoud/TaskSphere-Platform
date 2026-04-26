package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Comment;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.CommentCreateRequest;
import com.tasksphere.core.dto.CommentResponse;
import com.tasksphere.core.dto.CommentUpdateRequest;
import com.tasksphere.core.service.CommentManager;
import com.tasksphere.core.service.TaskManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : CommentController (API REST des commentaires)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskController.java) :
 * ────────────────────────────
 * Ce contrôleur est le point d'entrée HTTP pour les commentaires.
 * Il ne contient AUCUNE logique métier — il délègue au CommentManager.
 *
 * ENDPOINTS :
 * ──────────
 * GET    /api/v1/tasks/{taskId}/comments     → Lister les commentaires d'une tâche
 * POST   /api/v1/tasks/{taskId}/comments     → Créer un commentaire
 * PUT    /api/v1/comments/{commentId}        → Modifier un commentaire
 * DELETE /api/v1/comments/{commentId}        → Supprimer un commentaire
 *
 * CONNEXION FRONTEND ↔ BACKEND :
 * ─────────────────────────────────
 * Le hook useComments.ts appelle ces endpoints :
 * - getTaskComments(taskId) → GET /tasks/{taskId}/comments
 * - createComment(taskId, {content}) → POST /tasks/{taskId}/comments
 * - updateComment(commentId, {content}) → PUT /comments/{commentId}
 * - deleteComment(commentId) → DELETE /comments/{commentId}
 *
 * NOUVEAU — DÉPENDANCE SUR TaskManager :
 * ─────────────────────────────────────────
 * Ce contrôleur injecte aussi TaskManager pour vérifier que l'utilisateur
 * a le droit de voir la tâche AVANT d'afficher/ajouter des commentaires.
 * C'est le même pattern que le RBAC de TaskController.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentManager commentManager;
    private final TaskManager taskManager;

    // ═══════════════════════════════════════════════════════
    // LISTE DES COMMENTAIRES D'UNE TÂCHE
    // ═══════════════════════════════════════════════════════

    /**
     * GET /api/v1/tasks/{taskId}/comments
     *
     * Récupère tous les commentaires d'une tâche.
     * Vérifie d'abord que l'utilisateur a le droit de voir la tâche (RBAC).
     */
    @GetMapping("/api/v1/tasks/{taskId}/comments")
    public ResponseEntity<?> getComments(
            @PathVariable String taskId,
            Authentication authentication) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : GET /tasks/{}/comments — {}", taskId, username);

        // Vérifier que l'utilisateur a le droit de voir la tâche
        Optional<Task> taskOpt = taskManager.getTaskById(taskId, username, role);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
        }

        List<Comment> comments = commentManager.getCommentsByTaskId(taskId);
        List<CommentResponse> response = comments.stream()
                .map(CommentResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════
    // CRÉATION D'UN COMMENTAIRE
    // ═══════════════════════════════════════════════════════

    /**
     * POST /api/v1/tasks/{taskId}/comments
     *
     * Crée un nouveau commentaire sur une tâche.
     * @Valid active la validation Jakarta du DTO.
     */
    @PostMapping("/api/v1/tasks/{taskId}/comments")
    public ResponseEntity<?> createComment(
            @PathVariable String taskId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : POST /tasks/{}/comments — {}", taskId, username);

        // Vérifier que l'utilisateur a le droit de voir la tâche
        Optional<Task> taskOpt = taskManager.getTaskById(taskId, username, role);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
        }

        String taskTitle = taskOpt.get().title();
        Comment created = commentManager.createComment(
                request.content(), username, taskId, taskTitle);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.fromDomain(created));
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR (PROPRIÉTAIRE UNIQUEMENT)
    // ═══════════════════════════════════════════════════════

    /**
     * PUT /api/v1/comments/{commentId}
     *
     * Modifie un commentaire.
     * RBAC : seul le propriétaire (username === auteur) peut modifier.
     */
    @PutMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<?> updateComment(
            @PathVariable String commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : PUT /comments/{} — {}", commentId, username);

        // Récupérer le commentaire pour vérifier le propriétaire
        // Le CommentManager a une méthode findById interne via le port
        // Ici on délègue au service qui vérifie le propriétaire
        Optional<Comment> updated = commentManager.updateComment(
                commentId, request.content(), username);

        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Commentaire non trouvé"));
        }

        return ResponseEntity.ok(CommentResponse.fromDomain(updated.get()));
    }

    // ═══════════════════════════════════════════════════════
    // SUPPRESSION (PROPRIÉTAIRE OU ADMIN)
    // ═══════════════════════════════════════════════════════

    /**
     * DELETE /api/v1/comments/{commentId}
     *
     * Supprime un commentaire.
     * RBAC : le propriétaire ou un ADMIN peuvent supprimer.
     */
    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable String commentId,
            Authentication authentication) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : DELETE /comments/{} — {} (rôle: {})", commentId, username, role);

        boolean deleted = commentManager.deleteComment(commentId, username, role);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Commentaire non trouvé"));
        }

        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODE PRIVÉE UTILITAIRE
    // ═══════════════════════════════════════════════════════

    /** RAPPEL (de TaskController) : Extrait le rôle depuis les authorities. */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }
}