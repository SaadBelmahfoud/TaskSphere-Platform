package com.tasksphere.iam.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE REFRESH : RefreshTokenRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-6 : Remplacement de Map<String, String> par un DTO
 *
 * AVANT : @RequestBody Map<String, String> request → request.get("refreshToken")
 * APRÈS : @Valid @RequestBody RefreshTokenRequest request → request.refreshToken()
 *
 * PRINCIPE :
 * Le refresh token est un UUID opaque envoyé par le client pour
 * obtenir un nouveau JWT quand l'ancien a expiré.
 * Il DOIT être présent et non-vide.
 */
public record RefreshTokenRequest(

        /**
         * Le refresh token brut (UUID) envoyé par le client.
         * Format attendu : xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
         *
         * @NotBlank : obligatoire et non-vide
         * On ne valide PAS le format UUID car :
         * - La vérification est faite par le RefreshTokenService
         * - Un token invalide sera rejeté avec un 401 Unauthorized
         */
        @NotBlank(message = "Le refresh token est obligatoire")
        String refreshToken
) {}