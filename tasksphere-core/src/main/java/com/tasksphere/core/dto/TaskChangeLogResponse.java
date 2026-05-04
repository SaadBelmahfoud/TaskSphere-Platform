package com.tasksphere.core.dto;

import com.tasksphere.core.domain.TaskChangeLog;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse TaskChangeLog (Ce que le client reçoit)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : DTO pour l'historique détaillé des changements
 * ─────────────────────────────────────────────────────────────────────
 *
 * UTILISÉ PAR :
 * - GET /api/v1/tasks/{id}/changes → Liste des changements d'une tâche
 *
 * PRINCIPE UI — DIFF VISUEL :
 * ────────────────────────────
 * Le frontend peut afficher les changements comme un "diff" :
 * - oldValue en rouge (barré)
 * - newValue en vert
 * - fieldName comme label du champ modifié
 * - changedBy comme avatar de l'utilisateur
 * - changedAt comme timestamp relatif ("il y a 5 minutes")
 */
public record TaskChangeLogResponse(
        String id,
        String taskId,
        String fieldName,       // "title", "status", "priority", etc.
        String oldValue,        // Valeur avant (null si création)
        String newValue,        // Valeur après
        String changedBy,       // Email de l'utilisateur
        LocalDateTime changedAt
) {

    /**
     * Convertit un objet domaine TaskChangeLog en DTO de sortie.
     */
    public static TaskChangeLogResponse fromDomain(TaskChangeLog changeLog) {
        return new TaskChangeLogResponse(
                changeLog.id(),
                changeLog.taskId(),
                changeLog.fieldName(),
                changeLog.oldValue(),
                changeLog.newValue(),
                changeLog.changedBy(),
                changeLog.changedAt()
        );
    }
}