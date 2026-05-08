package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TagPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
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
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION ACTIVITY : Audit des opérations sur les tags
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   Les opérations sur les tags (création, suppression, association,
 *   retrait) n'étaient PAS tracées dans l'Activity Log.
 *
 * APRÈS :
 *   - createOrGetTag → TAG_CREATED (si nouveau tag créé)
 *   - deleteTag → TAG_DELETED
 *   - addTagToTask → TAG_ADDED_TO_TASK (avec titre de la tâche)
 *   - removeTagFromTask → TAG_REMOVED_FROM_TASK (avec titre de la tâche)
 *
 * DÉPENDANCES AJOUTÉES :
 * - EventPublisherPort : pour publier les événements d'audit
 * - TaskPersistencePort : pour récupérer le titre de la tâche
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagPort tagPort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : Ports pour l'audit
     * ═══════════════════════════════════════════════════════════════════
     */
    private final EventPublisherPort eventPublisher;
    private final TaskPersistencePort taskPersistencePort;

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

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : Audit de la création de tag
        // ═══════════════════════════════════════════════════════════════════
        // Pas de taskId/taskTitle pour la création d'un tag (c'est global).
        // ═══════════════════════════════════════════════════════════════════
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_CREATED,
                "Tag '" + name + "' créé",
                createdBy,
                null,
                null
        ));

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

    /**
     * Associer un tag à une tâche.
     *
     * PHASE 3 — CORRECTION ACTIVITY : Audit de l'association tag↔tâche
     */
    @Transactional
    public void addTagToTask(String taskId, String tagId, String username) {
        log.info("SERVICE : Ajout du tag {} à la tâche {} par {}", tagId, taskId, username);
        tagPort.addTagToTask(taskId, tagId);

        // Récupérer les noms pour l'audit
        String tagName = tagPort.findById(tagId).map(Tag::name).orElse("Inconnu");
        String taskTitle = taskPersistencePort.findById(taskId)
                .map(Task::title)
                .orElse("Tâche inconnue");

        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_ADDED_TO_TASK,
                "Tag '" + tagName + "' ajouté à '" + taskTitle + "'",
                username,
                taskId,
                taskTitle
        ));
    }

    /**
     * Retirer un tag d'une tâche.
     *
     * PHASE 3 — CORRECTION ACTIVITY : Audit du retrait tag↔tâche
     */
    @Transactional
    public void removeTagFromTask(String taskId, String tagId, String username) {
        log.info("SERVICE : Retrait du tag {} de la tâche {} par {}", tagId, taskId, username);

        // Récupérer les noms AVANT le retrait pour l'audit
        String tagName = tagPort.findById(tagId).map(Tag::name).orElse("Inconnu");
        String taskTitle = taskPersistencePort.findById(taskId)
                .map(Task::title)
                .orElse("Tâche inconnue");

        tagPort.removeTagFromTask(taskId, tagId);

        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_REMOVED_FROM_TASK,
                "Tag '" + tagName + "' retiré de '" + taskTitle + "'",
                username,
                taskId,
                taskTitle
        ));
    }

    /** Trouver les tags d'une tâche. */
    @Transactional(readOnly = true)
    public List<Tag> getTagsByTaskId(String taskId) {
        return tagPort.findTagsByTaskId(taskId);
    }

    /**
     * Supprimer un tag.
     *
     * PHASE 3 — CORRECTION ACTIVITY : Audit de la suppression de tag
     */
    @Transactional
    public boolean deleteTag(String tagId, String username) {
        log.info("SERVICE : Suppression du tag {} par {}", tagId, username);

        var existingTag = tagPort.findById(tagId);
        if (existingTag.isEmpty()) return false;

        String tagName = existingTag.get().name();
        tagPort.deleteById(tagId);

        // Pas de taskId/taskTitle pour la suppression d'un tag (c'est global)
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_DELETED,
                "Tag '" + tagName + "' supprimé",
                username,
                null,
                null
        ));

        return true;
    }
}