package com.tasksphere.iam.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE DÉCONNEXION : LogoutRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-6 : Remplacement de Map<String, String> par un DTO
 *
 * AVANT : @RequestBody Map<String, String> request → request.get("refreshToken")
 * APRÈS : @Valid @RequestBody LogoutRequest request → request.refreshToken()
 *
 * PRINCIPE :
 * Pour la déconnexion, on a besoin du refresh token pour identifier
 * l'utilisateur et révoquer tous ses tokens. Le JWT access token
 * expirera naturellement (1h max), donc on n'a pas besoin de le
 * révoquer explicitement.
 */
public record LogoutRequest(

        /**
         * Le refresh token brut du client.
         * Utilisé pour identifier l'utilisateur et révoquer tous ses tokens.
         *
         * @NotBlank : obligatoire et non-vide
         */
        @NotBlank(message = "Le refresh token est obligatoire")
        String refreshToken
) {}