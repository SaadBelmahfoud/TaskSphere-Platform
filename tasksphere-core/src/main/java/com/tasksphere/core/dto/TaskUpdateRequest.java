package com.tasksphere.core.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/*
 * ====================================================================
 * DTO D'ENTRÉE : Modification de tâche
 * ====================================================================
 *
 * PRINCIPE :
 * Tous les champs sont optionnels. Le client n'envoie que les champs
 * qu'il veut modifier. Les autres restent inchangés.
 *
 * Exemple : pour changer uniquement la priorité, le client envoie :
 * { "priority": "HIGH" }
 * Le titre et la description ne seront pas modifiés.
 */
public record TaskUpdateRequest(

        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
        String description,

        String priority,   // "LOW", "MEDIUM", "HIGH", "CRITICAL"

        LocalDate dueDate  // Date d'échéance (doit être dans le futur si fournie)
) {
}