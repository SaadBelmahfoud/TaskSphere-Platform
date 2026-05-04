package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Attachment;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse Attachment
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 6 : DTO pour les pièces jointes
 * ────────────────────────────────────────────────
 *
 * NOTE : storageKey n'est PAS exposé dans le DTO pour des raisons de sécurité.
 * Le frontend utilise l'ID de l'attachment pour télécharger le fichier.
 */
public record AttachmentResponse(
        String id,
        String taskId,
        String fileName,
        String contentType,
        long fileSize,
        String uploadedBy,
        LocalDateTime uploadedAt
) {

    public static AttachmentResponse fromDomain(Attachment attachment) {
        return new AttachmentResponse(
                attachment.id(),
                attachment.taskId(),
                attachment.fileName(),
                attachment.contentType(),
                attachment.fileSize(),
                attachment.uploadedBy(),
                attachment.uploadedAt()
        );
    }
}