package com.tasksphere.core.dto;

import jakarta.validation.constraints.NotBlank;

/*
 * ====================================================================
 * DTO D'ENTRÉE : Changement de statut
 * ====================================================================
 *
 * PRINCIPE :
 * On sépare le changement de statut du PUT global.
 * Pourquoi ? C'est l'action la plus fréquente (cliquer "Marquer comme terminée"),
 * donc on lui donne un endpoint dédié plus simple.
 */
public record TaskStatusRequest(

        @NotBlank(message = "Le statut est obligatoire")
        String status   // "TODO", "DOING", "DONE"
) {
}