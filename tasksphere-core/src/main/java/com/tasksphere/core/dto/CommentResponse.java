package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Comment;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse commentaire (Ce que le client reçoit)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskResponse.java) :
 * - On ne renvoie JAMAIS l'entité JPA au client
 * - Le DTO contrôle les champs visibles
 * - Le mapper statique fromDomain() fait la conversion
 *
 * Ce DTO est utilisé par les endpoints :
 * - GET /tasks/{taskId}/comments → liste de CommentResponse
 * - POST /tasks/{taskId}/comments → un CommentResponse
 * - PUT /comments/{commentId} → un CommentResponse
 */
public record CommentResponse(
        String id,
        String content,
        String username,    // Email de l'auteur (pour affichage + RBAC edit/delete)
        String taskId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Convertit un objet domaine Comment en DTO de sortie.
     * Pattern : Static Factory Method de mapping.
     *
     * @param comment L'objet domaine à convertir
     * @return Le DTO prêt à être sérialisé en JSON pour le client
     */
    public static CommentResponse fromDomain(Comment comment) {
        return new CommentResponse(
                comment.id(),
                comment.content(),
                comment.username(),
                comment.taskId(),
                comment.createdAt(),
                comment.updatedAt()
        );
    }
}