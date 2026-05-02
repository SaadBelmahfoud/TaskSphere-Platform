package com.tasksphere.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE MISE À JOUR DE RÔLE : RoleUpdateRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-6 : Remplacement de Map<String, String> par un DTO
 *
 * AVANT (PROBLÈME) :
 *   @RequestBody Map<String, String> request → request.get("role")
 *   → Aucune validation : un rôle "HACKER" passait sans problème
 *   → La vérification manuelle List.of("USER","MANAGER","ADMIN").contains()
 *     est faite DANS le contrôleur → pas de séparation des responsabilités
 *
 * APRÈS (SOLUTION) :
 *   @Valid @RequestBody RoleUpdateRequest request
 *   → @Pattern valide le format du rôle AVANT d'atteindre le contrôleur
 *   → La validation est déclarative (annotation) pas impérative (if/else)
 *   → Le contrôleur est plus propre et plus maintenable
 *
 * PRINCIPE @Pattern :
 * - Valide que la chaîne correspond à l'expression régulière
 * - Ici : "USER|MANAGER|ADMIN" → exactement un des trois rôles
 * - Plus sûr que contains() car la regex est validée au plus tôt
 */
public record RoleUpdateRequest(

        /**
         * Nouveau rôle à attribuer à l'utilisateur.
         * Valeurs autorisées : USER, MANAGER, ADMIN
         *
         * @NotBlank : obligatoire
         * @Pattern : doit être exactement USER, MANAGER ou ADMIN
         *
         * PRINCIPE DE VALIDATION :
         * L'annotation @Pattern est évaluée AVANT le code du contrôleur.
         * Si le rôle est invalide → 400 Bad Request automatique
         * avec le message d'erreur de l'annotation.
         */
        @NotBlank(message = "Le rôle est obligatoire")
        @Pattern(regexp = "USER|MANAGER|ADMIN", message = "Rôle invalide. Valeurs possibles : USER, MANAGER, ADMIN")
        String role
) {}