package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Tag;
import com.tasksphere.core.dto.TagCreateRequest;
import com.tasksphere.core.dto.TagResponse;
import com.tasksphere.core.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : TagController (API REST Tags)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : API REST pour les tags/labels
 * ─────────────────────────────────────────────────
 *
 * ENDPOINTS :
 * ──────────
 * POST   /api/v1/tags                   → Créer un tag (ou récupérer l'existant)
 * GET    /api/v1/tags                   → Lister tous les tags
 * GET    /api/v1/tags/{id}              → Détail d'un tag
 * DELETE /api/v1/tags/{id}              → Supprimer un tag
 * POST   /api/v1/tasks/{taskId}/tags/{tagId}    → Associer un tag à une tâche
 * DELETE /api/v1/tasks/{taskId}/tags/{tagId}     → Retirer un tag d'une tâche
 * GET    /api/v1/tasks/{taskId}/tags     → Lister les tags d'une tâche
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * POST /api/v1/tags — Créer un nouveau tag.
     * Si le nom existe déjà, retourne le tag existant (Get or Create).
     */
    @PostMapping("/tags")
    public ResponseEntity<?> createTag(
            @Valid @RequestBody TagCreateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : POST /tags — '{}' par {}", request.name(), username);

        Tag tag = tagService.createOrGetTag(request.name(), request.color(), username);
        TagResponse response = TagResponse.fromDomain(tag);

        return ResponseEntity.created(URI.create("/api/v1/tags/" + response.id())).body(response);
    }

    /**
     * GET /api/v1/tags — Lister tous les tags.
     */
    @GetMapping("/tags")
    public ResponseEntity<?> getAllTags() {
        log.info("CONTROLLER : GET /tags — Liste de tous les tags");
        List<TagResponse> tags = tagService.getAllTags().stream()
                .map(TagResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(tags);
    }

    /**
     * GET /api/v1/tags/{id} — Détail d'un tag.
     */
    @GetMapping("/tags/{id}")
    public ResponseEntity<?> getTagById(@PathVariable String id) {
        log.info("CONTROLLER : GET /tags/{}", id);
        Tag tag = tagService.getTagById(id);
        if (tag == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tag non trouvé"));
        }
        return ResponseEntity.ok(TagResponse.fromDomain(tag));
    }

    /**
     * DELETE /api/v1/tags/{id} — Supprimer un tag.
     */
    @DeleteMapping("/tags/{id}")
    public ResponseEntity<?> deleteTag(@PathVariable String id, Authentication authentication) {
        log.info("CONTROLLER : DELETE /tags/{} par {}", id, authentication.getName());
        boolean deleted = tagService.deleteTag(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tag non trouvé"));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/tasks/{taskId}/tags/{tagId} — Associer un tag à une tâche.
     */
    @PostMapping("/tasks/{taskId}/tags/{tagId}")
    public ResponseEntity<?> addTagToTask(
            @PathVariable String taskId,
            @PathVariable String tagId,
            Authentication authentication) {

        log.info("CONTROLLER : POST /tasks/{}/tags/{} par {}", taskId, tagId, authentication.getName());
        tagService.addTagToTask(taskId, tagId);
        return ResponseEntity.ok(Map.of("message", "Tag associé à la tâche"));
    }

    /**
     * DELETE /api/v1/tasks/{taskId}/tags/{tagId} — Retirer un tag d'une tâche.
     */
    @DeleteMapping("/tasks/{taskId}/tags/{tagId}")
    public ResponseEntity<?> removeTagFromTask(
            @PathVariable String taskId,
            @PathVariable String tagId,
            Authentication authentication) {

        log.info("CONTROLLER : DELETE /tasks/{}/tags/{} par {}", taskId, tagId, authentication.getName());
        tagService.removeTagFromTask(taskId, tagId);
        return ResponseEntity.ok(Map.of("message", "Tag retiré de la tâche"));
    }

    /**
     * GET /api/v1/tasks/{taskId}/tags — Lister les tags d'une tâche.
     */
    @GetMapping("/tasks/{taskId}/tags")
    public ResponseEntity<?> getTaskTags(@PathVariable String taskId) {
        log.info("CONTROLLER : GET /tasks/{}/tags", taskId);
        List<TagResponse> tags = tagService.getTagsByTaskId(taskId).stream()
                .map(TagResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(tags);
    }
}