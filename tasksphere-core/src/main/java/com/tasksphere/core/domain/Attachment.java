package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : Attachment (Pièce jointe)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 6 : Upload de pièces jointes
 * ────────────────────────────────────────────────
 *
 * PRINCIPE — SÉPARATION MÉTADONNÉES / FICHIER :
 * ────────────────────────────────────────────────
 * L'objet Attachment contient les MÉTADONNÉES du fichier
 * (nom, type, taille, qui, quand). Le contenu binaire est
 * stocké séparément via FileStoragePort.
 *
 * POURQUOI PAS LE CONTENU DANS LE DOMAINE ?
 * ────────────────────────────────────────────
 * Le domaine ne doit PAS dépendre de l'infrastructure de stockage.
 * Le contenu binaire est géré par l'adaptateur de stockage.
 * Le domaine ne connaît que la clé de stockage (storageKey).
 */
public record Attachment(
        String id,              // UUID unique
        String taskId,          // ID de la tâche
        String fileName,        // Nom original du fichier
        String contentType,     // Type MIME (image/png, application/pdf, etc.)
        long fileSize,          // Taille en octets
        String storageKey,      // Clé dans le stockage (chemin unique)
        String uploadedBy,      // Email de l'utilisateur
        LocalDateTime uploadedAt // Date/heure de l'upload
) {

    /**
     * Factory Method : Crée une nouvelle pièce jointe.
     */
    public static Attachment create(String taskId, String fileName, String contentType,
                                    long fileSize, String storageKey, String uploadedBy) {
        return new Attachment(
                UUID.randomUUID().toString(),
                taskId,
                fileName,
                contentType,
                fileSize,
                storageKey,
                uploadedBy,
                LocalDateTime.now()
        );
    }
}