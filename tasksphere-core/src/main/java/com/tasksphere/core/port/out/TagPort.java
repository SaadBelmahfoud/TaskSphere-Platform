package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Tag;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance des tags
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Port pour les tags/labels
 * ─────────────────────────────────────────────────
 *
 * MÉTHODES :
 * ─────────
 * 1. save()             : Créer ou mettre à jour un tag
 * 2. findAll()          : Lister tous les tags
 * 3. findById()         : Trouver un tag par ID
 * 4. findByName()       : Trouver un tag par nom (insensible à la casse)
 * 5. deleteById()       : Supprimer un tag
 * 6. findTagsByTaskId() : Trouver les tags d'une tâche
 * 7. addTagToTask()     : Associer un tag à une tâche
 * 8. removeTagFromTask(): Retirer un tag d'une tâche
 */
public interface TagPort {

    /** Sauvegarder un tag (création ou mise à jour). */
    Tag save(Tag tag);

    /** Lister tous les tags. */
    List<Tag> findAll();

    /** Trouver un tag par ID. */
    Optional<Tag> findById(String id);

    /** Trouver un tag par nom (insensible à la casse). */
    Optional<Tag> findByName(String name);

    /** Supprimer un tag par ID. */
    void deleteById(String id);

    /** Trouver les tags associés à une tâche. */
    List<Tag> findTagsByTaskId(String taskId);

    /** Associer un tag à une tâche. */
    void addTagToTask(String taskId, String tagId);

    /** Retirer un tag d'une tâche. */
    void removeTagFromTask(String taskId, String tagId);
}