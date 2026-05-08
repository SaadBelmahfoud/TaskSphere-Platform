package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Attachment;
import com.tasksphere.core.dto.AttachmentResponse;
import com.tasksphere.core.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : AttachmentController (API REST Pièces jointes)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 6 : API REST pour l'upload de pièces jointes
 * ────────────────────────────────────────────────
 *
 * ENDPOINTS :
 * ──────────
 * POST   /api/v1/tasks/{taskId}/attachments → Upload une pièce jointe
 * GET    /api/v1/tasks/{taskId}/attachments → Liste les pièces jointes
 * GET    /api/v1/attachments/{id}/download  → Télécharge une pièce jointe
 * DELETE /api/v1/attachments/{id}           → Supprime une pièce jointe
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION UPLOAD : Amélioration du diagnostic
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (PROBLÈME) :
 *   Le catch(Exception e) ne logguait que e.getMessage(), ce qui
 *   masquait la cause racine (AccessDeniedException) et rendait
 *   le diagnostic impossible.
 *
 * APRÈS :
 *   - Log la stack trace complète à ERROR
 *   - Inclut le nom du fichier dans le message d'erreur
 *   - Le message retourné au frontend reste générique (sécurité)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 3 — CORRECTION ACTIVITY : Username passé au service pour audit
 * ═══════════════════════════════════════════════════════════════════
 * La méthode deleteAttachment nécessite maintenant le username
 * pour publier l'événement d'audit ATTACHMENT_DELETED.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * POST /api/v1/tasks/{taskId}/attachments
     *
     * Upload une pièce jointe pour une tâche.
     * Utilise multipart/form-data pour envoyer le fichier.
     */
    @PostMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<?> uploadAttachment(
            @PathVariable String taskId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : POST /tasks/{}/attachments — '{}' par {}",
                taskId, file.getOriginalFilename(), username);

        try {
            Attachment attachment = attachmentService.uploadAttachment(
                    taskId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream(),
                    username
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(AttachmentResponse.fromDomain(attachment));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // ═══════════════════════════════════════════════════════════
            // PHASE 3 — CORRECTION UPLOAD : Log complet de l'exception
            // ═══════════════════════════════════════════════════════════
            // AVANT : log.error("CONTROLLER : Échec de l'upload — {}", e.getMessage())
            //   → Seul le message "Échec du stockage du fichier : xxx.pdf" était loggué
            //   → La cause (AccessDeniedException) était perdue
            //
            // APRÈS : Log la stack trace complète pour un diagnostic rapide.
            //   Le message frontend reste générique pour ne pas exposer
            //   des détails internes (chemins, permissions, etc.)
            // ═══════════════════════════════════════════════════════════
            log.error("CONTROLLER : Échec de l'upload du fichier '{}' pour la tâche {}",
                    file.getOriginalFilename(), taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Échec de l'upload du fichier"));
        }
    }

    /**
     * GET /api/v1/tasks/{taskId}/attachments
     *
     * Liste les pièces jointes d'une tâche.
     */
    @GetMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<?> getTaskAttachments(@PathVariable String taskId) {
        log.info("CONTROLLER : GET /tasks/{}/attachments", taskId);
        List<AttachmentResponse> attachments = attachmentService.getAttachmentsByTaskId(taskId).stream()
                .map(AttachmentResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(attachments);
    }

    /**
     * GET /api/v1/attachments/{id}/download
     *
     * Télécharge le contenu binaire d'une pièce jointe.
     */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<?> downloadAttachment(@PathVariable String id) {
        log.info("CONTROLLER : GET /attachments/{}/download", id);

        Attachment attachment = attachmentService.getAttachmentById(id);
        if (attachment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Pièce jointe non trouvée"));
        }

        InputStream inputStream = attachmentService.downloadAttachment(id);
        if (inputStream == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Fichier non trouvé sur le stockage"));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.fileName() + "\"")
                .body(new InputStreamResource(inputStream));
    }

    /**
     * DELETE /api/v1/attachments/{id}
     *
     * Supprime une pièce jointe.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 3 — CORRECTION ACTIVITY : Username passé au service pour audit
     * ═══════════════════════════════════════════════════════════════════
     * AVANT : attachmentService.deleteAttachment(id)
     * APRÈS : attachmentService.deleteAttachment(id, username)
     *   → L'événement ATTACHMENT_DELETED peut enregistrer l'acteur
     * ═══════════════════════════════════════════════════════════════════
     */
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<?> deleteAttachment(@PathVariable String id, Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : DELETE /attachments/{} par {}", id, username);
        boolean deleted = attachmentService.deleteAttachment(id, username);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Pièce jointe non trouvée"));
        }
        return ResponseEntity.noContent().build();
    }
}