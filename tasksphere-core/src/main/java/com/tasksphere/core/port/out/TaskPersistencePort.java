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
 * SPRINT 1 : Ajout de findById, update, et filtrage par utilisateur.
 */
public interface TaskPersistencePort {

    /** Sauvegarder une tâche (création ou mise à jour) */
    Task save(Task task);

    /** Récupérer toutes les tâches actives d'un utilisateur avec pagination */
    Page<Task> findByUserId(String userId, Pageable pageable);

    /** Récupérer une tâche active par son ID */
    Optional<Task> findById(String id);

    /** Récupérer une tâche active par ID et par utilisateur (pour vérifier l'ownership) */
    Optional<Task> findByIdAndUserId(String id, String userId);

    /** Supprimer logiquement une tâche (soft delete) */
    void softDelete(String id);
}