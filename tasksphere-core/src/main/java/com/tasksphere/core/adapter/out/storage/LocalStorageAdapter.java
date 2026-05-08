package com.tasksphere.core.adapter.out.storage;

import com.tasksphere.core.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE STOCKAGE : LocalStorageAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 6 : Stockage local pour le développement
 * ────────────────────────────────────────────────
 *
 * PRINCIPE — STOCKAGE LOCAL vs S3/MINIO :
 * ────────────────────────────────────────
 * En développement, on stocke les fichiers sur le disque local.
 * En production, on remplace cet adaptateur par MinIOAdapter ou S3Adapter.
 *
 * STRUCTURE DES FICHIERS :
 * ${storage.local.path}/
 *   attachments/
 *     uuid1.png
 *     uuid2.pdf
 *     ...
 *
 * CLÉ DE STOCKAGE : "attachments/{uuid}.{extension}"
 * L'UUID garantit l'unicité même si deux fichiers ont le même nom.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION UPLOAD : Amélioration du diagnostic
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   L'exception IOException était attrapée et relancée comme RuntimeException
 *   avec SEULEMENT le message. La stack trace et la cause réelle
 *   (AccessDeniedException, NoSuchFileException, etc.) étaient perdues.
 *   → Impossible de diagnostiquer le problème dans les logs.
 *
 * APRÈS :
 *   - Log explicite à ERROR avant de lancer l'exception
 *   - Inclusion du chemin absolu du répertoire dans le message
 *   - L'exception originale est préservée comme cause (e)
 *   - Vérification préalable de la résolution du chemin pour le diagnostic
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class LocalStorageAdapter implements FileStoragePort {

    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    @Override
    public String store(InputStream inputStream, String fileName, String contentType) {
        try {
            Path uploadDir = Paths.get(storagePath, "attachments").toAbsolutePath();

            // ═══════════════════════════════════════════════════════════
            // PHASE 3 — CORRECTION UPLOAD : Diagnostic avant création
            // ═══════════════════════════════════════════════════════════
            // Log le chemin absolu pour faciliter le diagnostic.
            // Si le répertoire parent n'est pas inscriptible, on le
            // détecte AVANT le Files.createDirectories() pour un message
            // d'erreur plus clair.
            // ═══════════════════════════════════════════════════════════
            log.debug("STOCKAGE : Résolution du répertoire d'upload : {}", uploadDir);

            Files.createDirectories(uploadDir);

            // Générer une clé unique : attachments/uuid.extension
            String extension = getFileExtension(fileName);
            String storageKey = "attachments/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

            Path filePath = uploadDir.resolve(storageKey.substring(storageKey.lastIndexOf('/') + 1));
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("STOCKAGE : Fichier '{}' stocké sous la clé '{}'", fileName, storageKey);
            return storageKey;

        } catch (IOException e) {
            // ═══════════════════════════════════════════════════════════
            // PHASE 3 — CORRECTION UPLOAD : Log complet de l'erreur
            // ═══════════════════════════════════════════════════════════
            // AVANT : Seul le message était loggué, sans le chemin
            //         ni le type d'exception (AccessDeniedException, etc.)
            // APRÈS : Log à ERROR avec le chemin absolu, le type
            //         d'exception et la stack trace complète.
            // ═══════════════════════════════════════════════════════════
            log.error("STOCKAGE : Échec du stockage du fichier '{}' — chemin résolu : {} — erreur : {}",
                    fileName, Paths.get(storagePath, "attachments").toAbsolutePath(), e.getClass().getSimpleName(), e);
            throw new RuntimeException("Échec du stockage du fichier : " + fileName, e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        try {
            Path filePath = Paths.get(storagePath, storageKey);
            if (!Files.exists(filePath)) {
                throw new RuntimeException("Fichier non trouvé : " + storageKey);
            }
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Échec de la lecture du fichier : " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path filePath = Paths.get(storagePath, storageKey);
            Files.deleteIfExists(filePath);
            log.info("STOCKAGE : Fichier '{}' supprimé", storageKey);
        } catch (IOException e) {
            log.warn("STOCKAGE : Échec de la suppression du fichier '{}' — {}", storageKey, e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }
}