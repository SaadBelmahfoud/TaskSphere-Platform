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
 * CHAÎNE DE VALIDATION :
 * 1. Client envoie le JSON
 * 2. Spring désérialise en TaskCreateRequest
 * 3. @Valid déclenche la validation Jakarta
 * 4. Si erreur → MethodArgumentNotValidException → 400 Bad Request
 * 5. Si OK → le contrôleur reçoit un objet valide
 *
 * PRINCIPE DU RECORD COMME DTO D'ENTRÉE :
 * Un record est parfait car le client envoie un JSON plat.
 * Spring (Jackson) mappe automatiquement les champs JSON sur les paramètres du record.
 *
 * FLOW DE VALIDATION COMPLET :
 * Client → JSON → Jackson (désérialisation) → @Valid (validation Jakarta)
 * → Controller (objet valide) → Service (logique métier) → Repository (persistance)
 */
public record TaskCreateRequest(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
        String description,

        /**
         * Priority optionnel : "LOW", "MEDIUM", "HIGH", "CRITICAL"
         * Si null ou vide → la valeur par défaut (MEDIUM) sera appliquée par le service.
         *
         * NOTE : Pas d'annotation @Pattern ici car la validation de la valeur
         * de l'enum est faite dans le service avec Task.TaskPriority.valueOf().
         * On aurait pu utiliser @Pattern mais le message d'erreur serait moins clair.
         */
        String priority,

        /**
         * Date d'échéance optionnelle.
         * Si null → pas de date d'échéance.
         * TODO (futur) : Ajouter @FutureOrPresent pour interdire les dates passées.
         */
        LocalDate dueDate
) {
}