package com.tasksphere.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO D'ENTRÉE : Création d'un commentaire
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskCreateRequest.java) :
 * Un DTO d'entrée reçoit les données du client HTTP.
 * Les annotations Jakarta Validation (@NotBlank, @Size) vérifient
 * les contraintes AVANT que le contrôleur n'appelle le service.
 *
 * POURQUOI content MIN = 1 et MAX = 2000 ?
 * - @NotBlank : un commentaire vide n'a aucun sens
 * - @Size(max = 2000) : limite pour éviter les abus et les
 *   performances dégradées. 2000 caractères est généreux pour un commentaire.
 */
public record CommentCreateRequest(
        @NotBlank(message = "Le contenu du commentaire est obligatoire")
        @Size(max = 2000, message = "Le commentaire ne doit pas dépasser 2000 caractères")
        String content
) {}