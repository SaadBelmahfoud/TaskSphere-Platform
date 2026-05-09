package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TagPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE MÉTIER : TagService
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Gérer le cycle de vie des tags (labels de catégorisation)
 * et leurs associations avec les tâches.
 *
 * PHASE 3 — FEATURE 3 : Service pour les tags/labels
 * ─────────────────────────────────────────────────
 *
 * MÉTHODES EXPOSÉES :
 * ──────────────────
 * 1. createOrGetTag()      : Créer un tag ou récupérer l'existant (Get or Create)
 * 2. getAllTags()          : Lister tous les tags
 * 3. getTagById()          : Détail d'un tag par ID
 * 4. deleteTag()           : Supprimer un tag (et ses associations)
 * 5. addTagToTask()        : Associer un tag à une tâche
 * 6. removeTagFromTask()   : Retirer un tag d'une tâche
 * 7. getTagsByTaskId()     : Lister les tags d'une tâche
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION T1 : TagService publie les événements d'audit
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 *   TaskManager appelait directement tagPort.addTagToTask() et
 *   tagPort.removeTagFromTask(). Ces appels de bas niveau ne
 *   publiaient PAS d'événements d'audit → les actions TAG_ADDED_TO_TASK
 *   et TAG_REMOVED_FROM_TASK n'apparaissaient JAMAIS dans l'Activity Log.
 *
 * SOLUTION :
 *   TaskManager appelle maintenant tagService.addTagToTask() et
 *   tagService.removeTagFromTask(). Ces méthodes :
 *   1. Appellent le port de persistance (tagPort) pour la DB
 *   2. Publient un événement d'audit (TaskAuditEvent) pour la traçabilité
 *
 *   Le port (tagPort) fait le QUOI technique (SQL INSERT/DELETE).
 *   Le service (tagService) fait le POURQUOI métier (audit + logique).
 *
 * PRINCIPE — COUCHE SERVICE vs COUCHE PORT :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  TagService.addTagToTask(taskId, tagId, username)               │
 * │    1. tagPort.addTagToTask(taskId, tagId) → persistance         │
 * │    2. tagPort.findById(tagId) → récupérer le nom du tag         │
 * │    3. eventPublisher.publishAuditEvent(...) → traçabilité       │
 * │                                                                  │
 * │  tagPort.addTagToTask(taskId, tagId)                            │
 * │    → INSERT INTO task_tags VALUES (taskId, tagId)               │
 * │    → PAS d'audit, PAS de notification                           │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION ACTIVITY : Username passé aux méthodes d'audit
 * ═══════════════════════════════════════════════════════════════════
 * Les méthodes addTagToTask, removeTagFromTask et deleteTag
 * nécessitent le username pour publier les événements d'audit
 * (TaskAuditEvent.username). Ce username est fourni par le
 * contrôleur (via Authentication.getName()) et par TaskManager
 * (via le contexte de sécurité).
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    /** Port de persistance pour les opérations CRUD sur les tags. */
    private final TagPort tagPort;

    /**
     * Port de publication d'événements domaine.
     * Utilisé pour publier les événements d'audit (TAG_CREATED, TAG_DELETED,
     * TAG_ADDED_TO_TASK, TAG_REMOVED_FROM_TASK).
     */
    private final EventPublisherPort eventPublisher;

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TAG (Get or Create)
    // ═══════════════════════════════════════════════════════

    /**
     * Crée un nouveau tag ou retourne le tag existant si le nom existe déjà.
     *
     * PRINCIPE — GET OR CREATE :
     * Si un tag avec le même nom (insensible à la casse) existe déjà,
     * on le retourne sans en créer un nouveau. Cela évite les doublons
     * et les conflits d'unicité en base de données.
     *
     * @param name      Nom du tag (sera stocké tel quel)
     * @param color     Couleur hexadécimale (null → couleur par défaut)
     * @param username  Email de l'utilisateur qui crée le tag
     * @return Le tag créé ou existant
     */
    @Transactional
    public Tag createOrGetTag(String name, String color, String username) {
        log.info("SERVICE : Création/récupération du tag '{}' par {}", name, username);

        // Vérifier si le tag existe déjà (insensible à la casse)
        Optional<Tag> existingTag = tagPort.findByName(name);
        if (existingTag.isPresent()) {
            log.debug("SERVICE : Tag '{}' existe déjà (id: {})", name, existingTag.get().id());
            return existingTag.get();
        }

        // Créer le nouveau tag
        Tag tag = Tag.create(name, color, username);
        Tag savedTag = tagPort.save(tag);

        // Publier l'événement d'audit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_CREATED,
                "Tag '" + savedTag.name() + "' créé",
                username,
                null,       // Pas de tâche associée à la création d'un tag
                null        // Pas de titre de tâche
        ));

        log.info("SERVICE : Tag créé avec succès (id: {}, name: {})", savedTag.id(), savedTag.name());
        return savedTag;
    }

    // ═══════════════════════════════════════════════════════
    // LECTURE DES TAGS
    // ═══════════════════════════════════════════════════════

    /**
     * Récupère tous les tags.
     *
     * @return La liste de tous les tags
     */
    @Transactional(readOnly = true)
    public List<Tag> getAllTags() {
        log.debug("SERVICE : Liste de tous les tags");
        return tagPort.findAll();
    }

    /**
     * Récupère un tag par son ID.
     *
     * @param id L'ID du tag
     * @return Le tag, ou null si non trouvé
     */
    @Transactional(readOnly = true)
    public Tag getTagById(String id) {
        log.debug("SERVICE : Recherche du tag {}", id);
        return tagPort.findById(id).orElse(null);
    }

    /**
     * Récupère les tags associés à une tâche.
     *
     * @param taskId L'ID de la tâche
     * @return La liste des tags de la tâche
     */
    @Transactional(readOnly = true)
    public List<Tag> getTagsByTaskId(String taskId) {
        log.debug("SERVICE : Tags de la tâche {}", taskId);
        return tagPort.findTagsByTaskId(taskId);
    }

    // ═══════════════════════════════════════════════════════
    // SUPPRESSION DE TAG
    // ═══════════════════════════════════════════════════════

    /**
     * Supprime un tag par son ID.
     *
     * PRINCIPE :
     * La suppression d'un tag supprime aussi implicitement ses associations
     * avec les tâches (ON DELETE CASCADE dans la table task_tags).
     *
     * @param id       L'ID du tag à supprimer
     * @param username Email de l'utilisateur qui supprime le tag
     * @return true si le tag a été supprimé, false s'il n'existait pas
     */
    @Transactional
    public boolean deleteTag(String id, String username) {
        log.info("SERVICE : Suppression du tag {} par {}", id, username);

        Optional<Tag> tagOpt = tagPort.findById(id);
        if (tagOpt.isEmpty()) {
            log.warn("SERVICE : Tag {} non trouvé pour suppression", id);
            return false;
        }

        Tag tag = tagOpt.get();
        String tagName = tag.name();

        // Supprimer le tag (les associations task_tags sont supprimées par CASCADE)
        tagPort.deleteById(id);

        // Publier l'événement d'audit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_DELETED,
                "Tag '" + tagName + "' supprimé",
                username,
                null,       // Pas de tâche spécifique
                null        // Pas de titre de tâche
        ));

        log.info("SERVICE : Tag '{}' supprimé avec succès", tagName);
        return true;
    }

    // ═══════════════════════════════════════════════════════
    // ASSOCIATION TAG ↔ TÂCHE
    // ═══════════════════════════════════════════════════════

    /**
     * Associe un tag à une tâche.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION T1 : Audit pour l'association tag↔tâche
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (BUG) :
     *   tagPort.addTagToTask(taskId, tagId) → INSERT uniquement
     *   → Pas d'événement d'audit → ACTION INVISIBLE dans l'Activity Log
     *
     * APRÈS :
     *   1. tagPort.addTagToTask(taskId, tagId) → persistance
     *   2. eventPublisher.publishAuditEvent(TAG_ADDED_TO_TASK, ...) → traçabilité
     *   → L'action apparaît maintenant dans l'Activity Log
     *
     * @param taskId   L'ID de la tâche
     * @param tagId    L'ID du tag à associer
     * @param username Email de l'utilisateur qui fait l'association
     */
    @Transactional
    public void addTagToTask(String taskId, String tagId, String username) {
        log.info("SERVICE : Association du tag {} à la tâche {} par {}", tagId, taskId, username);

        // 1. Persister l'association (INSERT INTO task_tags)
        tagPort.addTagToTask(taskId, tagId);

        // 2. Récupérer le nom du tag pour la description de l'audit
        String tagName = tagPort.findById(tagId)
                .map(Tag::name)
                .orElse("inconnu");

        // 3. Publier l'événement d'audit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_ADDED_TO_TASK,
                "Tag '" + tagName + "' ajouté à la tâche",
                username,
                taskId,
                null        // taskTitle sera rempli par le listener si possible
        ));

        log.debug("SERVICE : Tag '{}' associé à la tâche {} avec audit", tagName, taskId);
    }

    /**
     * Retire un tag d'une tâche.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION T1 : Audit pour la dissociation tag↔tâche
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (BUG) :
     *   tagPort.removeTagFromTask(taskId, tagId) → DELETE uniquement
     *   → Pas d'événement d'audit → ACTION INVISIBLE dans l'Activity Log
     *
     * APRÈS :
     *   1. tagPort.removeTagFromTask(taskId, tagId) → persistance
     *   2. eventPublisher.publishAuditEvent(TAG_REMOVED_FROM_TASK, ...) → traçabilité
     *   → L'action apparaît maintenant dans l'Activity Log
     *
     * @param taskId   L'ID de la tâche
     * @param tagId    L'ID du tag à retirer
     * @param username Email de l'utilisateur qui fait la dissociation
     */
    @Transactional
    public void removeTagFromTask(String taskId, String tagId, String username) {
        log.info("SERVICE : Retrait du tag {} de la tâche {} par {}", tagId, taskId, username);

        // 1. Récupérer le nom du tag AVANT la suppression (pour l'audit)
        String tagName = tagPort.findById(tagId)
                .map(Tag::name)
                .orElse("inconnu");

        // 2. Supprimer l'association (DELETE FROM task_tags)
        tagPort.removeTagFromTask(taskId, tagId);

        // 3. Publier l'événement d'audit
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.TAG_REMOVED_FROM_TASK,
                "Tag '" + tagName + "' retiré de la tâche",
                username,
                taskId,
                null        // taskTitle sera rempli par le listener si possible
        ));

        log.debug("SERVICE : Tag '{}' retiré de la tâche {} avec audit", tagName, taskId);
    }
}