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
 */
public record TaskCreateRequest(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
        String description,

        String priority,   // "LOW", "MEDIUM", "HIGH", "CRITICAL" (optionnel, défaut = MEDIUM)

        LocalDate dueDate  // Date d'échéance (optionnelle, pas de date passée)
) {
}