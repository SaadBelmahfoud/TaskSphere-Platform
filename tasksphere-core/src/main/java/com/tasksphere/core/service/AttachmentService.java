package com.tasksphere.core.service;

import com.tasksphere.core.domain.Attachment;
import com.tasksphere.core.port.out.AttachmentPort;
import com.tasksphere.core.port.out.FileStoragePort;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentPort attachmentPort;
    private final FileStoragePort fileStoragePort;

    /** Taille maximale des fichiers : 10 Mo */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * Upload une pièce jointe pour une tâche.
     *
     * FLUX :
     * 1. Vérifier la taille du fichier
     * 2. Stocker le fichier binaire via FileStoragePort
     * 3. Enregistrer les métadonnées via AttachmentPort
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

    /** Supprimer une pièce jointe. */
    @Transactional
    public boolean deleteAttachment(String attachmentId) {
        Attachment attachment = attachmentPort.findById(attachmentId).orElse(null);
        if (attachment == null) return false;

        // Supprimer le fichier binaire
        fileStoragePort.delete(attachment.storageKey());

        // Supprimer les métadonnées
        attachmentPort.deleteById(attachmentId);

        log.info("SERVICE : Pièce jointe '{}' supprimée", attachmentId);
        return true;
    }
}