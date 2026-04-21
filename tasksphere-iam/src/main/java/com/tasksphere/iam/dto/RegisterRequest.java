package com.tasksphere.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO D'INSCRIPTION : RegisterRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * Record Java utilisé comme DTO de validation.
 * Les annotations Jakarta Validation garantissent que les données
 * reçues du client respectent les contraintes.
 *
 * VALIDATION CÔTÉ SERVEUR (@Valid dans le contrôleur) :
 * ────────────────────────────────────────────────────
 * - username : obligatoire, 3-50 caractères
 * - firstName : obligatoire, max 100 caractères
 * - lastName : obligatoire, max 100 caractères
 * - email : obligatoire, format email valide
 * - password : obligatoire, 6-100 caractères
 * - confirmPassword : obligatoire (la vérification password === confirmPassword
 *   est faite explicitement dans AuthController.register())
 *
 * VALIDATION CÔTÉ CLIENT (Zod dans le frontend) :
 * ────────────────────────────────────────────────────
 * Le même schéma est dupliqué dans src/lib/schemas.ts (registerSchema).
 * Cela garantit une validation immédiate (sans aller au serveur)
 * tout en gardant la validation serveur comme filet de sécurité.
 *
 * POURQUOI DUPLIQUER LA VALIDATION ?
 * ────────────────────────────────────
 * 1. UX : Le client valide instantanément (pas de latence réseau)
 * 2. Sécurité : Le serveur ne fait JAMAIS confiance au client
 *    (un utilisateur peut bypasser la validation frontend)
 * 3. Les deux couches sont indépendantes (backend Java, frontend TypeScript)
 */
public record RegisterRequest(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
        String username,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
        String lastName,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas au bon format")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 6, max = 100, message = "Le mot de passe doit contenir entre 6 et 100 caractères")
        String password,

        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword
) {}