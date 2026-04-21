package com.tasksphere.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/*
 * ====================================================================
 * DTO D'ENTRÉE : Création de tâche (Ce que le client nous envoie)
 * ====================================================================
 *
 * PRINCIPE DE VALIDATION :
 * Les annotations Jakarta Validation (@NotBlank, @Size) sont vérifiées automatiquement
 * par Spring quand on met @Valid dans le contrôleur.
 * Si une validation échoue, Spring retourne automatiquement une 400 Bad Request
 * avec le détail des erreurs.
 *
 * PRINCIPE @NotBlank vs @NotNull :
 * - @NotNull : ne peut pas être null (mais "" est accepté)
 * - @NotBlank : ne peut pas être null ET ne peut pas être vide ("" ou "  ")
 * - @NotEmpty : ne peut pas être null ET ne peut pas être vide ("" accepte "  ")
 *
 * ASSIGNATION (Option A) :
 * Le champ assigneeId permet de créer une tâche directement assignée.
 * - Si null ou absent du JSON → tâche non assignée (valeur par défaut)
 * - Si renseigné → la tâche sera assignée à cet utilisateur
 *   (le service vérifie que seul ADMIN/MANAGER peut assigner)
 *
 * PRINCIPE DE SÉCURITÉ JACKSON :
 * Quand le client n'envoie pas assigneeId dans le JSON, Jackson met null.
 * On n'a pas besoin de @JsonInclude ou de valeur par défaut — null est la bonne
 * valeur pour signifier "pas d'assignation".
 */
public record TaskCreateRequest(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
        String description,

        /**
         * Priority optionel : "LOW", "MEDIUM", "HIGH", "CRITICAL"
         * Si null ou vide → la valeur par défaut (MEDIUM) sera appliquée par le service.
         */
        String priority,

        /**
         * Date d'échéance optionnelle.
         * Si null → pas de date d'échéance.
         */
        LocalDate dueDate,

        /**
         * Assignataire optionnel (Option A d'assignation).
         * Un MANAGER ou ADMIN peut créer une tâche déjà assignée.
         * Si null ou absent du JSON → tâche non assignée.
         */
        String assigneeId
) {
}