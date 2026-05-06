package com.tasksphere.core.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

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
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — FEATURE 3 : Ajout du champ tagIds
 * ═══════════════════════════════════════════════════════════════════
 * Le champ tagIds permet de remplacer la liste complète des tags associés.
 * - Si null → les tags ne sont PAS modifiés (comportement PATCH standard)
 * - Si renseigné (même liste vide) → la liste des tags est remplacée :
 *   - Les tags présents dans la nouvelle liste mais pas dans l'ancienne sont ajoutés
 *   - Les tags présents dans l'ancienne liste mais pas dans la nouvelle sont retirés
 *   - Les tags présents dans les deux listes restent associés
 *
 * NOTE : Ce comportement de "synchronisation" (et non d'ajout incrémental)
 * est le standard REST pour les relations Many-to-Many. Il permet au
 * frontend d'envoyer l'état COMPLET souhaité sans calculer le diff.
 * ═══════════════════════════════════════════════════════════════════
 */
public record TaskUpdateRequest(

        @Size(min = 3, max = 255, message = "Le titre doit contenir entre 3 et 255 caractères")
        String title,

        @Size(max = 5000, message = "La description ne peut pas dépasser 5000 caractères")
        String description,

        String priority,   // "LOW", "MEDIUM", "HIGH", "CRITICAL"

        LocalDate dueDate,  // Date d'échéance (doit être dans le futur si fournie)

        String assigneeId,

        /**
         * ═══════════════════════════════════════════════════════════════════
         * PHASE 3 — FEATURE 3 : IDs des tags pour synchronisation
         * ═══════════════════════════════════════════════════════════════════
         * Liste optionnelle d'IDs de tags. Si fournie, remplace la liste
         * complète des tags associés à la tâche (synchronisation).
         * Si null → les tags ne sont PAS modifiés.
         * ═══════════════════════════════════════════════════════════════════
         */
        List<String> tagIds
) {
}