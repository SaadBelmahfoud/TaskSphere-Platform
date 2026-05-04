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
 */
@Slf4j
@Component
public class LocalStorageAdapter implements FileStoragePort {

    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    @Override
    public String store(InputStream inputStream, String fileName, String contentType) {
        try {
            Path uploadDir = Paths.get(storagePath, "attachments");
            Files.createDirectories(uploadDir);

            // Générer une clé unique : attachments/uuid.extension
            String extension = getFileExtension(fileName);
            String storageKey = "attachments/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

            Path filePath = uploadDir.resolve(storageKey.substring(storageKey.lastIndexOf('/') + 1));
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("STOCKAGE : Fichier '{}' stocké sous la clé '{}'", fileName, storageKey);
            return storageKey;

        } catch (IOException e) {
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