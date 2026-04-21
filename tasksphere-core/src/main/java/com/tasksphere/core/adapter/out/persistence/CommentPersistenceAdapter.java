package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Comment;
import com.tasksphere.core.port.out.CommentPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : CommentPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskPersistenceAdapter.java) :
 * ─────────────────────────────────────────
 * Schéma de flux complet :
 * Controller → Service → Port (interface) → Adaptateur (CETTE CLASSE) → Repository → BDD
 *
 * IMPLEMENTATION DU PORT CommentPersistencePort :
 * ───────────────────────────────────────────
 * Cette classe traduit les appels du domaine en opérations JPA.
 * Le domaine ne sait pas que cette classe existe (il ne voit que l'interface).
 *
 * CHEMIN DUAL POUR INSERT/UPDATE :
 * ─────────────────────────────────
 * RAPPEL du problème NonUniqueObjectException (de TaskPersistenceAdapter) :
 * Si on fait new Entity + em.persist() alors qu'une entité avec le même ID
 * existe dans le L1 cache → erreur.
 *
 * SOLUTION (même pattern que TaskPersistenceAdapter) :
 * - INSERT : findById() retourne empty → new CommentEntity(comment) + save()
 * - UPDATE : findById() retourne l'entité → setContent() → dirty checking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentPersistenceAdapter implements CommentPersistencePort {

    private final CommentRepository commentRepository;

    /**
     * Sauvegarde un commentaire (INSERT ou UPDATE).
     *
     * RAPPEL : Le chemin dual évite le NonUniqueObjectException.
     */
    @Override
    public Comment save(Comment comment) {
        log.debug("ADAPTATEUR JPA : Sauvegarde du commentaire (id: {}, taskId: {})",
                comment.id(), comment.taskId());

        Optional<CommentEntity> existing = commentRepository.findById(comment.id());

        if (existing.isPresent()) {
            // ═══ CHEMIN UPDATE : l'entité existe en BDD ═══
            CommentEntity managed = existing.get();
            managed.setContent(comment.content());
            // Dirty checking de Hibernate → UPDATE SQL au commit
            return managed.toDomain();
        } else {
            // ═══ CHEMIN INSERT : nouvelle entité ═══
            CommentEntity entity = new CommentEntity(comment);
            CommentEntity saved = commentRepository.save(entity);
            return saved.toDomain();
        }
    }

    @Override
    public Optional<Comment> findById(String id) {
        return commentRepository.findById(id).map(CommentEntity::toDomain);
    }

    @Override
    public List<Comment> findByTaskIdOrderByCreatedAtDesc(String taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(CommentEntity::toDomain)
                .toList();
    }

    /**
     * Suppression physique d'un commentaire.
     * Pas de soft delete : les commentaires sont des données secondaires.
     * La trace de suppression est conservée dans l'ActivityLog.
     */
    @Override
    public void deleteById(String id) {
        log.debug("ADAPTATEUR JPA : Suppression du commentaire (id: {})", id);
        commentRepository.deleteById(id);
    }
}