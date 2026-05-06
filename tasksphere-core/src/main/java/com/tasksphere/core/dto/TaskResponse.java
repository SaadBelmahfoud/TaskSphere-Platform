package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
 * ====================================================================
 * DTO DE SORTIE : Réponse tâche (Ce que le client reçoit)
 * ====================================================================
 *
 * PRINCIPE DU DTO (Data Transfer Object) :
 * On ne renvoie JAMAIS l'entité JPA directement au client.
 * Le DTO contrôle exactement quels champs sont visibles.
 *
 * CORRECTION PACKAGE :
 * ──────────────────────
 * Ce fichier se trouve dans le répertoire dto/, il DOIT donc déclarer
 * package com.tasksphere.core.dto (et non com.tasksphere.core.domain).
 *
 * AVANT : package com.tasksphere.core.domain
 *   → Mismatch entre le répertoire physique (dto/) et la déclaration de package
 *   → Erreur "bad source file" : Java ne peut pas résoudre la classe
 *   → Erreur "duplicate class" : le compilateur voit 2 classes TaskResponse
 *     dans le package domain (celle-ci + une implicite)
 *   → CASCADE : Toutes les références à TaskResponse dans TaskController échouent
 *   → CASCADE : Lombok @Slf4j n'est pas traité → 60+ erreurs "cannot find symbol: log"
 *
 * APRÈS : package com.tasksphere.core.dto
 *   → Le package correspond au répertoire → compilation OK
 *   → TaskController importe com.tasksphere.core.dto.TaskResponse → résolution OK
 *   → Lombok @Slf4j est traité → les variables log sont générées → plus d'erreurs
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
 *
 * PRINCIPE D'IMPORT DU DOMAINE :
 * ──────────────────────────────
 * Ce DTO importe com.tasksphere.core.domain.Task pour convertir
 * un objet domaine en DTO de sortie via les méthodes fromDomain().
 * C'est une dépendance UNIDIRECTIONNELLE acceptable :
 * - Le DTO (couche de présentation) connaît le domaine
 * - Le domaine ne connaît PAS les DTOs
 * → Le flux de dépendance va du haut vers le bas, pas l'inverse.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — FEATURE 3 : Ajout du champ tags dans la réponse
 * ═══════════════════════════════════════════════════════════════════
 * Le champ tags contient la liste des TagResponse associés à la tâche.
 * Cela permet au frontend d'afficher les tags directement sans
 * requête supplémentaire (denormalization pour la performance).
 *
 * PRINCIPE DE DENORMALIZATION :
 * On pourrait ne renvoyer que les tagIds et laisser le frontend faire
 * des requêtes séparées pour les détails des tags. Mais cela
 * nécessiterait N+1 requêtes (1 pour la tâche + 1 par tag).
 * En incluant les tags directement, on réduit à 1 seule requête.
 * ═══════════════════════════════════════════════════════════════════
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
        String assigneeId,    // Personne assignée (null si non assignée)

        /**
         * ═══════════════════════════════════════════════════════════════════
         * PHASE 3 — FEATURE 3 : Tags associés à la tâche
         * ═══════════════════════════════════════════════════════════════════
         * Liste des tags associés à la tâche (denormalized).
         * Null si les tags n'ont pas été chargés (par exemple dans une liste
         * paginée où on ne charge pas les tags pour la performance).
         * Non-null si les tags ont été chargés (par exemple dans le détail
         * d'une tâche via GET /tasks/{id}).
         * ═══════════════════════════════════════════════════════════════════
         */
        List<TagResponse> tags
) {

    /**
     * Convertit un objet domaine Task en DTO de sortie (sans tags).
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
                task.assigneeId(),
                null    // ← Tags non chargés par défaut (performance)
        );
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — FEATURE 3 : Conversion avec tags
     * ═══════════════════════════════════════════════════════════════════
     * Surcharge qui inclut les tags associés à la tâche.
     * Utilisée quand le frontend a besoin d'afficher les tags
     * (par exemple dans le détail d'une tâche ou dans le Kanban).
     * ═══════════════════════════════════════════════════════════════════
     */
    public static TaskResponse fromDomainWithTags(Task task, List<TagResponse> tags) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.status() != null ? task.status().name() : "TODO",
                task.priority() != null ? task.priority().name() : "MEDIUM",
                task.dueDate(),
                task.completedAt(),
                task.createdAt(),
                task.userId(),
                task.assigneeId(),
                tags
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
                task.assigneeId(),
                null    // ← Tags non chargés par défaut
        );
    }
}