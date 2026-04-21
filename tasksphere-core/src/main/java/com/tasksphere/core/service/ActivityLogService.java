package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.port.out.ActivityLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE TRANSVERSAUX : ActivityLogService (Audit Log)
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — CROSS-CUTTING CONCERN :
 * ─────────────────────────────────────────
 * Ce service est appelé par PLUSIEURS autres services :
 * - TaskManager → logue les actions CRUD sur les tâches
 * - CommentManager → logue les actions CRUD sur les commentaires
 * - AdminController (IAM) → logue les changements de rôle
 *
 * C'est un "service d'application" (Application Service) qui coordonne
 * l'écriture dans l'audit log. Il n'a pas de logique métier complexe,
 * il se contente de créer un ActivityLog et de le sauvegarder.
 *
 * POURQUOI @Transactional ?
 * → L'écriture du log doit se faire dans la même transaction
 *   que l'action qui l'a déclenchée. Si l'action échoue (rollback),
 *   le log est aussi annulé. C'est le comportement souhaité : on ne
 *   log que les actions RÉELLEMENT effectuées.
 *
 * FLUX D'APPEL TYPIQUE :
 * ─────────────────────
 * TaskManager.createTask()
 *   → Task saved = persistencePort.save(task)       // Sauvegarde la tâche
 *   → activityLogService.log(TASK_CREATED, ...)      // Log l'action
 *   → Les DEUX opérations sont dans la MÊME transaction
 *
 * POURQUOI les descriptions sont formatées ici et pas dans le contrôleur ?
 * → Le service est plus proche du domaine. Il connaît le contexte métier.
 * → Le contrôleur ne doit pas contenir de logique de formatage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogPort activityLogPort;

    /**
     * Enregistre une action dans l'audit log.
     *
     * @param action      Le type d'action (de l'enum ActivityLog.Action)
     * @param description Description humaine de l'action
     * @param username    Email de l'utilisateur qui fait l'action
     * @param taskId      ID de la tâche concernée (null si hors contexte)
     * @param taskTitle   Titre de la tâche (denormalized pour affichage direct)
     */
    @Transactional
    public void log(ActivityLog.Action action, String description,
                    String username, String taskId, String taskTitle) {
        log.debug("ACTIVITY LOG : {} par {} — {}", action, username, description);
        ActivityLog activityLog = ActivityLog.create(action, description, username, taskId, taskTitle);
        activityLogPort.save(activityLog);
    }
}