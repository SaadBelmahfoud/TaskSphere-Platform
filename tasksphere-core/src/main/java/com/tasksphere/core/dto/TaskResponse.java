package com.tasksphere.core.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * ====================================================================
 * DTO DE SORTIE : Réponse tâche (Ce que le client reçoit)
 * ====================================================================
 *
 * PRINCIPE DU DTO (Data Transfer Object) :
 * On ne renvoie JAMAIS l'entité JPA directement au client.
 * Le DTO contrôle exactement quels champs sont visibles.
 *
 * CORRECTION B3 : createdAt n'est plus null dans fromDomain()
 * ────────────────────────────────────────────────────────
 * AVANT : createdAt était toujours null dans les réponses GET
 *   → fromDomain() hardcodait null : "createdAt n'est pas dans le domaine"
 *   → Le frontend ne pouvait PAS afficher la date de création
 *   → Le dashboard "Créées cette semaine" montrait toujours 0
 *
 * APRÈS : createdAt est récupéré depuis le domaine Task
 *   → Le domaine Task a maintenant un champ createdAt
 *   → fromDomain() utilise task.createdAt() au lieu de null
 *   → Le frontend reçoit la vraie date de création
 */
public record TaskResponse(
        String id,
        String title,
        String description,
        String status,        // String et pas Enum → le client reçoit "TODO"
        String priority,      // String et pas Enum → même principe
        LocalDate dueDate,
        LocalDateTime completedAt,
        LocalDateTime createdAt,   // ← CORRECTION B3 : N'est plus null
        String userId,        // Créateur de la tâche
        String assigneeId     // Personne assignée (null si non assignée)
) {

    /**
     * Convertit un objet domaine Task en DTO de sortie.
     *
     * CORRECTION B3 : Utilise task.createdAt() au lieu de null.
     */
    public static TaskResponse fromDomain(Task task) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.status() != null ? task.status().name() : "TODO",
                task.priority() != null ? task.priority().name() : "MEDIUM",
                task.dueDate(),
                task.completedAt(),
                task.createdAt(),    // ← CORRECTION B3 : était null, maintenant réel
                task.userId(),
                task.assigneeId()
        );
    }

    /**
     * Surcharge pour inclure la date de création depuis l'entité JPA.
     * CORRECTION B3 : Plus nécessaire car createdAt est dans le domaine,
     * mais gardée pour compatibilité descendante.
     */
    public static TaskResponse fromDomainWithDates(Task task, LocalDateTime createdAt) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.status() != null ? task.status().name() : "TODO",
                task.priority() != null ? task.priority().name() : "MEDIUM",
                task.dueDate(),
                task.completedAt(),
                task.createdAt() != null ? task.createdAt() : createdAt,  // ← CORRECTION B3
                task.userId(),
                task.assigneeId()
        );
    }
}