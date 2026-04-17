package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * ====================================================================
 * DTO DE SORTIE : Réponse tâche (Ce que le client reçoit)
 * ====================================================================
 *
 * PRINCIPE :
 * On ne renvoie JAMAIS l'entité JPA directement au client.
 * Le DTO contrôle exactement quels champs sont visibles.
 * Si on ajoute un champ interne (ex: "internalNote"), il n'apparaîtra pas ici.
 */
public record TaskResponse(
        String id,
        String title,
        String description,
        String status,
        String priority,
        LocalDate dueDate,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        String userId
) {

    /** Convertit un objet domaine Task en DTO de sortie */
    public static TaskResponse fromDomain(Task task) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.status() != null ? task.status().name() : "TODO",
                task.priority() != null ? task.priority().name() : "MEDIUM",
                task.dueDate(),
                task.completedAt(),
                null,
                task.userId()
        );
    }

    /** Surcharge pour inclure la date de création depuis l'entité JPA */
    public static TaskResponse fromDomainWithDates(Task task, LocalDateTime createdAt) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.status() != null ? task.status().name() : "TODO",
                task.priority() != null ? task.priority().name() : "MEDIUM",
                task.dueDate(),
                task.completedAt(),
                createdAt,
                task.userId()
        );
    }
}