package com.tasksphere.core.service;

import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.port.out.TagPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE : TagService (Gestion des tags/labels)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Service métier pour les tags
 * ─────────────────────────────────────────────────
 *
 * RESPONSABILITÉS :
 * ──────────────────
 * 1. Créer des tags (avec vérification d'unicité du nom)
 * 2. Lister tous les tags
 * 3. Associer/retirer des tags aux tâches
 * 4. Supprimer des tags
 *
 * RÈGLE MÉTIER — UNICITÉ DU NOM :
 * ─────────────────────────────────
 * Deux tags ne peuvent pas avoir le même nom (insensible à la casse).
 * Si un utilisateur tente de créer "Bug" alors que "bug" existe déjà,
 * le service retourne le tag existant au lieu d'en créer un nouveau.
 * C'est le pattern "Get or Create" (obtenir ou créer).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagPort tagPort;

    /**
     * Crée un nouveau tag ou retourne le tag existant si le nom existe déjà.
     *
     * PATTERN "GET OR CREATE" :
     * ──────────────────────────
     * 1. Chercher un tag avec le même nom (insensible à la casse)
     * 2. S'il existe → le retourner
     * 3. S'il n'existe pas → le créer
     *
     * AVANTAGE : Pas d'erreur 409 Conflict si le tag existe déjà.
     * Le frontend peut appeler createTag() sans vérifier au préalable.
     */
    @Transactional
    public Tag createOrGetTag(String name, String color, String createdBy) {
        log.info("SERVICE : Création/récupération du tag '{}' par {}", name, createdBy);

        // Vérifier si un tag avec le même nom existe déjà
        var existingTag = tagPort.findByName(name);
        if (existingTag.isPresent()) {
            log.info("SERVICE : Tag '{}' existe déjà (id: {})", name, existingTag.get().id());
            return existingTag.get();
        }

        Tag newTag = Tag.create(name, color, createdBy);
        Tag saved = tagPort.save(newTag);
        log.info("SERVICE : Tag '{}' créé avec succès (id: {})", name, saved.id());
        return saved;
    }

    /** Lister tous les tags. */
    @Transactional(readOnly = true)
    public List<Tag> getAllTags() {
        return tagPort.findAll();
    }

    /** Trouver un tag par ID. */
    @Transactional(readOnly = true)
    public Tag getTagById(String id) {
        return tagPort.findById(id).orElse(null);
    }

    /** Associer un tag à une tâche. */
    @Transactional
    public void addTagToTask(String taskId, String tagId) {
        log.info("SERVICE : Ajout du tag {} à la tâche {}", tagId, taskId);
        tagPort.addTagToTask(taskId, tagId);
    }

    /** Retirer un tag d'une tâche. */
    @Transactional
    public void removeTagFromTask(String taskId, String tagId) {
        log.info("SERVICE : Retrait du tag {} de la tâche {}", tagId, taskId);
        tagPort.removeTagFromTask(taskId, tagId);
    }

    /** Trouver les tags d'une tâche. */
    @Transactional(readOnly = true)
    public List<Tag> getTagsByTaskId(String taskId) {
        return tagPort.findTagsByTaskId(taskId);
    }

    /** Supprimer un tag. */
    @Transactional
    public boolean deleteTag(String tagId) {
        log.info("SERVICE : Suppression du tag {}", tagId);
        if (tagPort.findById(tagId).isEmpty()) return false;
        tagPort.deleteById(tagId);
        return true;
    }
}