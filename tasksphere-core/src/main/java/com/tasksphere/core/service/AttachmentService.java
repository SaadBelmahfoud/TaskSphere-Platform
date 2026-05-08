package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Attachment;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.domain.event.TaskAuditEvent;
import com.tasksphere.core.port.out.AttachmentPort;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.FileStoragePort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE : AttachmentService (Gestion des pièces jointes)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION ACTIVITY : Audit des pièces jointes
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   Les opérations d'upload et de suppression de pièces jointes
 *   n'étaient PAS tracées dans l'Activity Log. L'utilisateur ne
 *   voyait jamais ces actions dans le Dashboard.
 *
 * APRÈS :
 *   - Upload → publication d'un événement ATTACHMENT_UPLOADED
 *   - Suppression → publication d'un événement ATTACHMENT_DELETED
 *   - Ces événements sont traités par TaskAuditEventListener qui :
 *     1. Enregistre l'entrée dans l'Activity Log (post-commit)
 *     2. Envoie une notification WebSocket temps réel
 *
 * DÉPENDANCES AJOUTÉES :
 * - EventPublisherPort : pour publier les événements d'audit
 * - TaskPersistencePort : pour récupérer le titre de la tâche
 *   (nécessaire pour le champ denormalized taskTitle de l'ActivityLog)
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentPort attachmentPort;
    private final FileStoragePort fileStoragePort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : Injection des ports pour l'audit
     * ═══════════════════════════════════════════════════════════════════
     * EventPublisherPort : publie les événements d'audit (post-commit)
     * TaskPersistencePort : récupère le titre de la tâche pour le
     *   champ denormalized taskTitle dans l'ActivityLog
     * ═══════════════════════════════════════════════════════════════════
     */
    private final EventPublisherPort eventPublisher;
    private final TaskPersistencePort taskPersistencePort;

    /** Taille maximale des fichiers : 10 Mo */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * Upload une pièce jointe pour une tâche.
     *
     * FLUX :
     * 1. Vérifier la taille du fichier
     * 2. Stocker le fichier binaire via FileStoragePort
     * 3. Enregistrer les métadonnées via AttachmentPort
     * 4. Publier un événement d'audit ATTACHMENT_UPLOADED (post-commit)
     */
    @Transactional
    public Attachment uploadAttachment(String taskId, String fileName, String contentType,
                                       long fileSize, InputStream inputStream, String uploadedBy) {
        log.info("SERVICE : Upload de '{}' pour la tâche {} par {} ({} octets)",
                fileName, taskId, uploadedBy, fileSize);

        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Fichier trop volumineux. Maximum : " + (MAX_FILE_SIZE / 1024 / 1024) + " Mo");
        }

        // Stocker le fichier binaire
        String storageKey = fileStoragePort.store(inputStream, fileName, contentType);

        // Enregistrer les métadonnées
        Attachment attachment = Attachment.create(taskId, fileName, contentType, fileSize, storageKey, uploadedBy);
        Attachment saved = attachmentPort.save(attachment);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : Audit de l'upload
        // ═══════════════════════════════════════════════════════════════════
        // On récupère le titre de la tâche pour le champ denormalized
        // de l'ActivityLog. Si la tâche n'est pas trouvée (cas rare),
        // on utilise un titre par défaut.
        // ═══════════════════════════════════════════════════════════════════
        String taskTitle = taskPersistencePort.findById(taskId)
                .map(Task::title)
                .orElse("Tâche inconnue");

        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.ATTACHMENT_UPLOADED,
                "Pièce jointe '" + fileName + "' ajoutée",
                uploadedBy,
                taskId,
                taskTitle
        ));

        log.info("SERVICE : Pièce jointe '{}' uploadée avec succès (id: {})", fileName, saved.id());
        return saved;
    }

    /** Lister les pièces jointes d'une tâche. */
    @Transactional(readOnly = true)
    public List<Attachment> getAttachmentsByTaskId(String taskId) {
        return attachmentPort.findByTaskId(taskId);
    }

    /** Télécharger une pièce jointe. */
    @Transactional(readOnly = true)
    public InputStream downloadAttachment(String attachmentId) {
        Attachment attachment = attachmentPort.findById(attachmentId).orElse(null);
        if (attachment == null) return null;
        return fileStoragePort.retrieve(attachment.storageKey());
    }

    /** Trouver une pièce jointe par ID. */
    @Transactional(readOnly = true)
    public Attachment getAttachmentById(String id) {
        return attachmentPort.findById(id).orElse(null);
    }

    /**
     * Supprimer une pièce jointe.
     *
     * FLUX :
     * 1. Supprimer le fichier binaire
     * 2. Supprimer les métadonnées
     * 3. Publier un événement d'audit ATTACHMENT_DELETED (post-commit)
     */
    @Transactional
    public boolean deleteAttachment(String attachmentId, String deletedBy) {
        Attachment attachment = attachmentPort.findById(attachmentId).orElse(null);
        if (attachment == null) return false;

        // Récupérer le titre de la tâche AVANT suppression
        String taskTitle = taskPersistencePort.findById(attachment.taskId())
                .map(Task::title)
                .orElse("Tâche inconnue");

        // Supprimer le fichier binaire
        fileStoragePort.delete(attachment.storageKey());

        // Supprimer les métadonnées
        attachmentPort.deleteById(attachmentId);

        // ═══════════════════════════════════════════════════════════════════
        // PHASE 3 — CORRECTION ACTIVITY : Audit de la suppression
        // ═══════════════════════════════════════════════════════════════════
        eventPublisher.publishAuditEvent(new TaskAuditEvent(
                ActivityLog.Action.ATTACHMENT_DELETED,
                "Pièce jointe '" + attachment.fileName() + "' supprimée",
                deletedBy,
                attachment.taskId(),
                taskTitle
        ));

        log.info("SERVICE : Pièce jointe '{}' supprimée", attachmentId);
        return true;
    }
}