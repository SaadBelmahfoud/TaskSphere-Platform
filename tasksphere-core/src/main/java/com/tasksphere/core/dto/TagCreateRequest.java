package com.tasksphere.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO D'ENTRÉE : Requête de création de tag
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Validation Jakarta pour la création de tags
 * ─────────────────────────────────────────────────
 *
 * VALIDATIONS :
 * - name : obligatoire, 1-50 caractères, pas de caractères spéciaux dangereux
 * - color : optionnel, doit être un code hexadécimal valide (#RRGGBB)
 */
public record TagCreateRequest(
        @NotBlank(message = "Le nom du tag est obligatoire")
        @Size(min = 1, max = 50, message = "Le nom doit contenir entre 1 et 50 caractères")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit être au format hexadécimal (#RRGGBB)")
        String color
) {}