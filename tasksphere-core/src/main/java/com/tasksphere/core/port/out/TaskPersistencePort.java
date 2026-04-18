package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/*
 * ====================================================================
 * PORT SORTANT : Persistance des tâches (Contrat du domaine)
 * ====================================================================
 *
 * PRINCIPE DDD (Domain-Driven Design) :
 * Le domaine définit SON contrat. L'infrastructure (JPA, SQL) doit s'adapter.
 * C'est l'inversion de dépendance : le domaine dicte ses besoins, l'adapter obéit.
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * Un "Port" est une interface Java que le domaine expose.
 * - Port ENTRANT (in) : ce que le domaine offre (ex: Use Case interface)
 * - Port SORTANT (out) : ce dont le domaine a besoin (ex: persistance, messaging)
 *
 * ICI : C'est un port SORTANT car le domaine a besoin de sauvegarder/lire des tâches.
 * L'adaptateur (TaskPersistenceAdapter) implémentera cette interface.
 *
 * AVANTAGES DE CETTE APPROCHE :
 * 1. TESTABILITÉ : On peut mocker cette interface dans les tests unitaires
 * 2. FLEXIBILITÉ : On peut changer d'implémentation (JPA → MongoDB → Redis) sans toucher le domaine
 * 3. INDÉPENDANCE : Le domaine ne dépend d'aucune technologie spécifique
 *
 * SPRINT 1 : Ajout de findById, update, et filtrage par utilisateur.
 */
public interface TaskPersistencePort {

    /** Sauvegarder une tâche (création ou mise à jour) */
    Task save(Task task);

    /**
     * Récupérer toutes les tâches actives d'un utilisateur avec pagination.
     * "Actives" = deletedAt IS NULL (soft delete filter).
     */
    Page<Task> findByUserId(String userId, Pageable pageable);

    /** Récupérer une tâche active par son ID (deletedAt IS NULL) */
    Optional<Task> findById(String id);

    /**
     * Récupérer une tâche active par ID et par utilisateur (pour vérifier l'ownership).
     * Combine les deux filtres : id = ? AND userId = ? AND deletedAt IS NULL
     */
    Optional<Task> findByIdAndUserId(String id, String userId);

    /** Supprimer logiquement une tâche (soft delete : set deletedAt = now) */
    void softDelete(String id);
}