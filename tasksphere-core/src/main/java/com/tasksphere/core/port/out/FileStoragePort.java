package com.tasksphere.core.port.out;

import java.io.InputStream;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Stockage de fichiers
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 6 : Port pour l'upload de pièces jointes
 * ────────────────────────────────────────────────
 *
 * PRINCIPE — PORT DE STOCKAGE ABSTRAIT :
 * ────────────────────────────────────────
 * Ce port abstrait le stockage de fichiers. L'implémentation peut être :
 * - LocalStorageAdapter : fichiers sur le disque (développement)
 * - MinIOAdapter : serveur MinIO compatible S3 (production)
 * - S3Adapter : Amazon S3 (cloud)
 *
 * Changer de stockage ne modifie PAS le domaine, seul l'adaptateur change.
 * C'est le principe de l'architecture hexagonale : le domaine est indépendant.
 */
public interface FileStoragePort {

    /**
     * Stocke un fichier et retourne la clé de stockage unique.
     *
     * @param inputStream Le contenu du fichier
     * @param fileName    Le nom original du fichier
     * @param contentType Le type MIME du fichier
     * @return La clé de stockage unique (ex: "attachments/uuid.png")
     */
    String store(InputStream inputStream, String fileName, String contentType);

    /**
     * Récupère le contenu d'un fichier par sa clé de stockage.
     *
     * @param storageKey La clé de stockage
     * @return Le contenu du fichier
     */
    InputStream retrieve(String storageKey);

    /**
     * Supprime un fichier par sa clé de stockage.
     *
     * @param storageKey La clé de stockage
     */
    void delete(String storageKey);
}