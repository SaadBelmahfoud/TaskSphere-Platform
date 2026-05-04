package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.port.out.TagPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TagPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Implémentation du port TagPort
 * ─────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagPersistenceAdapter implements TagPort {

    private final TagRepository tagRepository;
    private final TaskTagRepository taskTagRepository;

    @Override
    public Tag save(Tag tag) {
        log.debug("ADAPTATEUR JPA : Sauvegarde du tag '{}' (id: {})", tag.name(), tag.id());
        TagEntity entity = new TagEntity(tag);
        TagEntity saved = tagRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<Tag> findAll() {
        log.debug("ADAPTATEUR JPA : Liste de tous les tags");
        return tagRepository.findAll().stream()
                .map(TagEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Tag> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche du tag {}", id);
        return tagRepository.findById(id)
                .map(TagEntity::toDomain);
    }

    @Override
    public Optional<Tag> findByName(String name) {
        log.debug("ADAPTATEUR JPA : Recherche du tag par nom '{}'", name);
        return tagRepository.findByNameIgnoreCase(name)
                .map(TagEntity::toDomain);
    }

    @Override
    public void deleteById(String id) {
        log.debug("ADAPTATEUR JPA : Suppression du tag {}", id);
        tagRepository.deleteById(id);
    }

    @Override
    public List<Tag> findTagsByTaskId(String taskId) {
        log.debug("ADAPTATEUR JPA : Recherche des tags pour la tâche {}", taskId);
        List<String> tagIds = taskTagRepository.findTagIdsByTaskId(taskId);
        return tagRepository.findAllById(tagIds).stream()
                .map(TagEntity::toDomain)
                .toList();
    }

    @Override
    public void addTagToTask(String taskId, String tagId) {
        log.debug("ADAPTATEUR JPA : Ajout du tag {} à la tâche {}", tagId, taskId);
        taskTagRepository.save(new TaskTagEntity(taskId, tagId));
    }

    @Override
    public void removeTagFromTask(String taskId, String tagId) {
        log.debug("ADAPTATEUR JPA : Retrait du tag {} de la tâche {}", tagId, taskId);
        taskTagRepository.deleteByTaskIdAndTagId(taskId, tagId);
    }
}