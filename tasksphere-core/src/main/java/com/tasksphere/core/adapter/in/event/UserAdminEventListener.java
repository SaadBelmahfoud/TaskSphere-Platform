package com.tasksphere.core.adapter.in.event;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.port.out.ActivityLogPort;
import com.tasksphere.iam.domain.UserAdminEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ═══════════════════════════════════════════════════════════════════
 * LISTENER D'ÉVÉNEMENTS ADMIN : UserAdminEventListener
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — EVENT LISTENER (Pattern Observer) :
 * ──────────────────────────────────────────────────────
 * Ce composant "écoute" les événements Spring publiés par n'importe
 * quel composant de l'application. Ici, il écoute UserAdminEvent.
 *
 * RAPPEL DU FLUX :
 * ──────────────────
 * AdminController (IAM)
 *   → publishEvent(UserAdminEvent)
 *     → Spring Event Bus transmet l'événement
 *       → CETTE CLASSE le reçoit via @EventListener
 *         → activityLogPort.save(ActivityLog.create(...))
 *
 * POURQUOI CE COMPONENT EST DANS Core ET PAS IAM ?
 * ────────────────────────────────────────────────────
 * - Il doit écrire dans l'ActivityLog (qui est dans Core)
 * - Il utilise ActivityLogPort (interface Core)
 * - IAM ne peut pas faire cet appel (dépendance cyclique)
 *
 * PRINCIPE @EventListener :
 * ────────────────────────
 * Quand Spring voit une méthode annotée @EventListener avec un
 * paramètre (ici UserAdminEvent), il inscrit automatiquement cette
 * méthode comme listener pour ce type d'événement.
 *
 * À l'exécution :
 * 1. eventPublisher.publishEvent(new UserAdminEvent(...)) est appelé
 * 2. Spring cherche tous les @EventListener pour UserAdminEvent
 * 3. Spring appelle onUserAdminEvent(event)
 *
 * PRINCIPE @Component :
 * Spring détecte cette classe au démarrage et crée un bean.
 * Le bean est automatiquement inscrit dans le système d'événements.
 *
 * PRINCIPE FAIL-SAFE :
 * Le try-catch garantit que si l'audit échoue, l'opération admin
 * (qui a déjà réussi avant la publication de l'événement) n'est pas
 * impactée. L'erreur est loggée mais pas propagée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAdminEventListener {

    private final ActivityLogPort activityLogPort;

    /**
     * Réagit aux événements d'administration utilisateur.
     *
     * MAP AdminAction → ActivityLog.Action :
     * ─────────────────────────────────────
     * ROLE_CHANGED  → USER_ROLE_CHANGED
     * USER_TOGGLED → USER_TOGGLED
     *
     * @param event L'événement publié par AdminController
     */
    @EventListener
    public void onUserAdminEvent(UserAdminEvent event) {
        try {
            // Traduire l'action admin en action d'audit log
            ActivityLog.Action auditAction = switch (event.getAction()) {
                case ROLE_CHANGED -> ActivityLog.Action.USER_ROLE_CHANGED;
                case USER_TOGGLED -> ActivityLog.Action.USER_TOGGLED;
            };

            // Créer et sauvegarder l'entrée d'audit
            // Note : taskId=null et taskTitle=null car l'action
            // ne concerne pas une tâche mais un utilisateur.
            ActivityLog logEntry = ActivityLog.create(
                    auditAction,
                    event.getDescription(),
                    event.getActorEmail(),
                    null,  // taskId = null (pas de tâche concernée)
                    null   // taskTitle = null
            );
            activityLogPort.save(logEntry);

            log.debug("AUDIT ADMIN : {} par {} — {}", auditAction, event.getActorEmail(), event.getDescription());
        } catch (Exception e) {
            // Fail-safe : l'audit ne doit JAMAIS faire échouer l'opération admin
            log.warn("AUDIT ADMIN : Échec de l'enregistrement — Action: {}, Cause: {}",
                    event.getAction(), e.getMessage());
        }
    }
}