package com.tasksphere.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO D'ENTRÉE : Modification d'un commentaire
 * ═══════════════════════════════════════════════════════════════════
 *
 * Même structure que CommentCreateRequest mais utilisé pour PUT.
 * Les deux ont les mêmes contraintes de validation.
 */
public record CommentUpdateRequest(
        @NotBlank(message = "Le contenu du commentaire est obligatoire")
        @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
        String content
) {}