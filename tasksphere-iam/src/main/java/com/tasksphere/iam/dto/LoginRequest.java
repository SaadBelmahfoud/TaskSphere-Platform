package com.tasksphere.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE CONNEXION : LoginRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-6 : Remplacement de Map<String, String> par un DTO
 *
 * AVANT (PROBLÈME) :
 *   @RequestBody Map<String, String> loginRequest
 *   → Aucune validation : email vide, password null = accepté
 *   → Pas de documentation Swagger
 *   → Risque de paramètres inattendus injectés par un attaquant
 *   → Typage faible : Object → risque ClassCastException
 *
 * APRÈS (SOLUTION) :
 *   @Valid @RequestBody LoginRequest loginRequest
 *   → Validation automatique par Jakarta Bean Validation
 *   → @Email vérifie le format email
 *   → @NotBlank empêche les champs vides
 *   → Documentation Swagger auto-générée
 *   → Typage fort : String partout, pas de cast
 *
 * PRINCIPE DU RECORD JAVA :
 *   - Immuabilité : les champs ne peuvent pas être modifiés après création
 *   - Concis : pas de getter/setter/equals/hashCode à écrire
 *   - Compatible avec Jackson (désérialisation JSON automatique)
 *   - compact() au lieu de new LoginRequest(email, password)
 */
public record LoginRequest(

        /**
         * Email de l'utilisateur.
         * @NotBlank : obligatoire et non-vide (après trim)
         * @Email : format email valide (regex RFC 5322 simplifiée)
         */
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas au bon format")
        String email,

        /**
         * Mot de passe en clair (sera comparé avec le hash BCrypt stocké).
         * @NotBlank : obligatoire (un login sans mot de passe n'a pas de sens)
         *
         * NOTE : On ne met PAS @Size(min=6) ici car :
         * - Le mot de passe peut être court si l'admin l'a configuré ainsi
         * - La contrainte de longueur est sur le REGISTER, pas le LOGIN
         * - Sinon un utilisateur avec un ancien mot de passe court ne pourrait plus se connecter
         */
        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {}