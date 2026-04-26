package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : CommentRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskRepository.java) :
 * ────────────────────────────
 * Spring Data JPA génère AUTOMATIQUEMENT l'implémentation.
 * On ne写 PAS de SQL : les noms de méthodes sont traduits en JPQL.
 *
 * MÉTHODES PAR QUERY DERIVATION :
 * ─────────────────────────────
 * findByTaskIdOrderByCreatedAtDesc :
 * → SELECT c FROM CommentEntity c WHERE c.taskId = :taskId
 *   ORDER BY c.createdAt DESC
 *
 * PAS DE FILTRE deletedAt ICI :
 * Contrairement aux tâches (soft delete), les commentaires utilisent
 * une suppression physique. Pas besoin de filtrer deletedAt IS NULL.
 */
@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, String> {

    /**
     * Récupère tous les commentaires d'une tâche, triés du plus récent au plus ancien.
     * Utilisé par : CommentPersistenceAdapter.findByTaskIdOrderByCreatedAtDesc()
     */
    List<CommentEntity> findByTaskIdOrderByCreatedAtDesc(String taskId);
}