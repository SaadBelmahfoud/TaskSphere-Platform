package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Task;

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
 * POURQUOI UN DTO ET PAS L'ENTITÉ DIRECTEMENT ?
 * 1. SÉCURITÉ : On cache les champs internes (ex: internalNote, auditLog)
 * 2. STABILITÉ : Si l'entité change, le DTO peut rester stable (contrat d'API)
 * 3. CONTRÔLE : On peut transformer les données (ex: enum → String)
 * 4. DOCUMENTATION : Le DTO définit clairement le contrat de l'API
 *
 * CHAMP assigneeId :
 * L'email de la personne assignée à la tâche (Option A d'assignation).
 * - null = tâche non assignée
 * - "email@x.com" = tâche assignée à cet utilisateur
 * Utilisé par le frontend pour afficher l'assignataire dans la Kanban et les cartes.
 */
public record TaskResponse(
        String id,
        String title,
        String description,
        String status,        // String et pas Enum → le client reçoit "TODO" et pas TaskStatus.TODO
        String priority,      // String et pas Enum → même principe
        LocalDate dueDate,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        String userId,        // Créateur de la tâche
        String assigneeId     // Personne assignée (null si non assignée)
) {

    /**
     * Convertit un objet domaine Task en DTO de sortie.
     *
     * PRINCIPE DE DÉFENSE :
     * On vérifie que status et priority ne sont pas null avant d'appeler .name().
     * C'est une protection contre les données corrompues ou les tests unitaires
     * où l'entité n'est pas complète.
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
                null,           // createdAt n'est pas dans le domaine → null
                task.userId(),
                task.assigneeId()
        );
    }

    /**
     * Surcharge pour inclure la date de création depuis l'entité JPA.
     * Utilisée typiquement après un save() où l'entité a été mise à jour par Hibernate.
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
                createdAt,
                task.userId(),
                task.assigneeId()
        );
    }
}