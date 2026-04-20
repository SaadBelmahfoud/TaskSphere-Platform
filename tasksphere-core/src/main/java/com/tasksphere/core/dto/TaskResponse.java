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
 * Si on ajoute un champ interne (ex: "internalNote"), il n'apparaîtra pas ici.
 *
 * POURQUOI UN DTO ET PAS L'ENTITÉ DIRECTEMENT ?
 * 1. SÉCURITÉ : On cache les champs internes (ex: internalNote, auditLog)
 * 2. STABILITÉ : Si l'entité change, le DTO peut rester stable (contrat d'API)
 * 3. CONTRÔLE : On peut transformer les données (ex: enum → String)
 * 4. DOCUMENTATION : Le DTO définit clairement le contrat de l'API
 *
 * PRINCIPE DU RECORD COMME DTO :
 * Un record est parfait pour un DTO car :
 * - Il est immuable (pas de modification après création)
 * - Il génère automatiquement getters, equals, hashCode, toString
 * - Il est concis (pas de boilerplate)
 *
 * PRINCIPE DE CONVERSION :
 * fromDomain() est une méthode statique factory qui convertit un objet domaine
 * en DTO. C'est le pattern "Factory Method" appliqué aux DTOs.
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
     * NOTE SUR createdAt :
     * Le domaine Task ne contient pas createdAt (c'est géré par l'entité JPA).
     * Ici, createdAt sera null. Pour inclure la vraie date de création,
     * utiliser fromDomainWithDates().
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
                task.assigneeId()   // ← Assignataire (null si non assigné)
        );
    }

    /**
     * Surcharge pour inclure la date de création depuis l'entité JPA.
     *
     * QUAND L'UTILISER :
     * Quand on a accès à l'entité JPA (ou à createdAt) ET au domaine.
     * Typiquement après un save() où l'entité a été mise à jour par Hibernate.
     *
     * PRINCIPE DE SURCHARGE :
     * On garde les deux méthodes pour la flexibilité :
     * - fromDomain(task) : rapide, quand on n'a pas besoin de createdAt
     * - fromDomainWithDates(task, createdAt) : complet, après un save()
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
                createdAt,     // ← Date de création réelle depuis l'entité JPA
                task.userId(),
                task.assigneeId()   // ← Assignataire (null si non assigné)
        );
    }
}