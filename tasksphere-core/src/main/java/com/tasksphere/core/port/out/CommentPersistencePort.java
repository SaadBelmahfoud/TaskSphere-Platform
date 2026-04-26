package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Comment;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance des commentaires (Contrat du domaine)
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskPersistencePort.java) :
 * ──────────────────────────────────────
 * Un port est une interface Java que le domaine expose.
 * C'est le DOMAINE qui dicte ses besoins (Inversion de Dépendance).
 * L'adaptateur (CommentPersistenceAdapter) implémentera cette interface.
 *
 * Ce port suit EXACTEMENT le même pattern que TaskPersistencePort :
 * - save() : insérer ou mettre à jour
 * - findById() : récupérer par ID
 * - findByTaskId() : récupérer tous les commentaires d'une tâche
 * - delete() : supprimer physiquement (les commentaires n'ont PAS de soft delete
 *   car ce sont des données secondaires, pas des données métier critiques)
 *
 * POURQUOI PAS DE SOFT DELETE SUR LES COMMENTAIRES ?
 * ────────────────────────────────────────────────────
 * Contrairement aux tâches (soft delete pour audit et récupération),
 * les commentaires sont des données secondaires :
 * - La suppression est définitive et intentionnelle
 * - On n'a pas besoin de récupérer un commentaire supprimé
 * - Les logs d'activité (ActivityLog) gardent la trace de la suppression
 * - Cela simplifie les requêtes (pas besoin de filtrer deletedAt IS NULL)
 */
public interface CommentPersistencePort {

    /**
     * Sauvegarde un commentaire (INSERT ou UPDATE).
     * Le CommentPersistenceAdapter utilisera le même pattern de
     * chemin dual que TaskPersistenceAdapter :
     * - Si l'ID existe en BDD → dirty checking (UPDATE)
     * - Si l'ID n'existe pas → nouvel entity (INSERT)
     */
    Comment save(Comment comment);

    /** Récupère un commentaire par son ID. */
    Optional<Comment> findById(String id);

    /**
     * Récupère tous les commentaires d'une tâche, triés par date de
     * création décroissante (les plus récents en premier).
     *
     * Utilisé par : CommentManager.getCommentsByTaskId()
     * Utilisé par le frontend : GET /tasks/{taskId}/comments
     */
    List<Comment> findByTaskIdOrderByCreatedAtDesc(String taskId);

    /** Supprime physiquement un commentaire. */
    void deleteById(String id);
}