package com.tasksphere.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO D'ASSIGNATION : AssignTaskRequest
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-6 : Remplacement de Map<String, String> par un DTO
 *
 * AVANT (PROBLÈME) :
 *   @RequestBody Map<String, String> request → request.get("assigneeId")
 *   → Aucune validation : un assigneeId vide ou mal formaté passait
 *   → Le format attendu est un email mais rien ne le vérifiait
 *
 * APRÈS (SOLUTION) :
 *   @Valid @RequestBody AssignTaskRequest request
 *   → @Email valide le format email de l'assigneeId
 *   → @NotBlank empêche les valeurs vides
 *
 * NOTE SUR assigneeId vs email :
 *   Dans l'implémentation actuelle, l'assigneeId est un email
 *   (le frontend envoie l'email de l'utilisateur à assigner).
 *   Le champ est nommé "assigneeId" pour rester cohérent avec
 *   l'API existante, mais il contient un email.
 */
public record AssignTaskRequest(

        /**
         * Email de l'utilisateur à qui assigner la tâche.
         *
         * @NotBlank : obligatoire (pour assigner, il faut désigner quelqu'un)
         * @Email : format email valide
         *
         * Pour désassigner : envoyer une chaîne vide "" ou null
         * → Le contrôleur vérifie si assigneeId est blank → désassignation
         *
         * NOTE : On utilise @NotBlank ET @Email ensemble.
         * @NotBlank empêche null et "" (après trim).
         * Si on veut permettre la désassignation, il faudrait
         * utiliser @Email(nullable = true) sans @NotBlank.
         * Pour l'instant, la désassignation se fait via une valeur vide
         * qui sera gérée dans le contrôleur.
         */
        @NotBlank(message = "L'identifiant de l'assignataire est obligatoire")
        @Email(message = "L'identifiant de l'assignataire doit être un email valide")
        String assigneeId
) {}