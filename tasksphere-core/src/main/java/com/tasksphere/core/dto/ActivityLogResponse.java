package com.tasksphere.core.dto;

import com.tasksphere.core.domain.ActivityLog;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse activité (Audit Log)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Utilisé par :
 * - Dashboard : activités récentes (embedded dans DashboardStatsResponse)
 * - ActivityLogController : endpoint GET /activities (paginé)
 *
 * Le champ "action" est un String (le nom de l'enum) car le frontend
 * le traite comme du texte pour l'affichage (icônes par type d'action).
 */
public record ActivityLogResponse(
        String id,
        String action,         // Nom de l'enum Action (ex: "TASK_CREATED")
        String description,    // Description humaine de l'action
        String username,
        String taskId,
        String taskTitle,      // Denormalized : titre de la tâche au moment de l'action
        LocalDateTime timestamp
) {
    public static ActivityLogResponse fromDomain(ActivityLog log) {
        return new ActivityLogResponse(
                log.id(),
                log.action(),
                log.description(),
                log.username(),
                log.taskId(),
                log.taskTitle(),
                log.timestamp()
        );
    }
}