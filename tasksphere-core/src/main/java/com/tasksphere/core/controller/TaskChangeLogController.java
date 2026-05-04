package com.tasksphere.core.controller;

import com.tasksphere.core.dto.TaskChangeLogResponse;
import com.tasksphere.core.port.out.TaskChangeLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : TaskChangeLogController (API REST Historique)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : API REST pour l'historique détaillé des changements
 * ─────────────────────────────────────────────────────────────────────
 *
 * ENDPOINT :
 * ──────────
 * GET /api/v1/tasks/{taskId}/changes?page=0&size=20
 *
 * RETOURNE : Liste paginée des changements de la tâche,
 * chacun contenant le champ modifié, l'ancienne et la nouvelle valeur.
 *
 * CONNEXION FRONTEND ↔ BACKEND :
 * ─────────────────────────────────
 * Le frontend appellera GET /tasks/{id}/changes pour afficher
 * l'historique détaillé dans un onglet "Changements" sur la page
 * de détail d'une tâche.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskChangeLogController {

    private final TaskChangeLogPort changeLogPort;

    /**
     * GET /api/v1/tasks/{taskId}/changes
     *
     * Récupère l'historique des changements d'une tâche avec pagination.
     *
     * @param taskId L'ID de la tâche
     * @param page   Numéro de page (0-based)
     * @param size   Taille de page (max 100)
     */
    @GetMapping("/{taskId}/changes")
    public ResponseEntity<?> getTaskChanges(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("CONTROLLER : GET /tasks/{}/changes (page: {}, size: {})", taskId, page, size);
        if (size > 100) size = 100;

        Page<TaskChangeLogResponse> changePage = changeLogPort.findByTaskId(
                        taskId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "changedAt"))
                )
                .map(TaskChangeLogResponse::fromDomain);

        var content = changePage.getContent();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalElements", changePage.getTotalElements(),
                "totalPages", changePage.getTotalPages(),
                "number", changePage.getNumber(),
                "size", changePage.getSize()
        ));
    }
}