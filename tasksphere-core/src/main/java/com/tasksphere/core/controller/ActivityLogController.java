package com.tasksphere.core.controller;

import com.tasksphere.core.dto.ActivityLogResponse;
import com.tasksphere.core.port.out.ActivityLogPort;
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
 * ADAPTATEUR D'ENTRÉE : ActivityLogController (Historique d'activité)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ENDPOINT :
 * ──────────
 * GET /api/v1/activities?page=0&size=20&taskId={optional}
 *
 * CONNEXION FRONTEND ↔ BACKEND :
 * ─────────────────────────────────
 * Le hook useActivityLog.ts appelle GET /activities avec pagination
 * et filtre optionnel par taskId.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogPort activityLogPort;

    @GetMapping
    public ResponseEntity<?> getActivities(
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("CONTROLLER : GET /activities (taskId: {}, page: {}, size: {})", taskId, page, size);
        if (size > 100) size = 100;

        Page<?> activityPage = activityLogPort.findAll(
                taskId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))
        );

        var content = activityPage.getContent().stream()
                .map(log -> (ActivityLogResponse) ActivityLogResponse.fromDomain(
                        (com.tasksphere.core.domain.ActivityLog) log))
                .toList();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalElements", activityPage.getTotalElements(),
                "totalPages", activityPage.getTotalPages(),
                "number", activityPage.getNumber(),
                "size", activityPage.getSize()
        ));
    }
}