package com.tasksphere.iam.domain;

import org.springframework.context.ApplicationEvent;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ÉVÉNEMENT ADMIN : UserAdminEvent
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — EVENT-DRIVEN ARCHITECTURE (Spring Events) :
 * ──────────────────────────────────────────────────────────
 * Un événement Spring (ApplicationEvent) est un message que n'importe
 * quel composant peut publier. Les listeners qui s'intéressent à cet
 * événement le reçoivent automatiquement.
 *
 * POURQUOI UN ÉVÉNEMENT ICI ?
 * ────────────────────────────
 * L'AdminController est dans le module IAM.
 * L'ActivityLog (audit trail) est dans le module Core.
 * IAM ne peut PAS dépendre de Core (dépendance cyclique Maven).
 *
 * SOLUTION : L'AdminController publie un événement Spring.
 * Un listener DANS Core capte l'événement et écrit dans l'audit log.
 *
 * FLUX :
 * ┌─────────────────┐   publishEvent()   ┌──────────────────────┐
 * │ AdminController │ ─────────────────→ │ Spring Event Bus      │
 * │ (module IAM)     │                   └──────────┬───────────┘
 * └─────────────────┘                              │
 *                                          @EventListener
 *                                                  ↓
 *                                         ┌──────────────────────┐
 *                                         │ UserAdminEventListener│
 *                                         │ (module Core)        │
 *                                         │ → activityLogPort    │
 *                                         │   .save(...)         │
 *                                         └──────────────────────┘
 *
 * AVANTAGES DE CE PATTERN :
 * 1. DÉCOUPLAGE : IAM ne connaît pas Core
 * 2. EXTENSIBILITÉ : On peut ajouter d'autres listeners sans modifier IAM
 * 3. ASYNCHRONE POTENTIEL : On peut rendre l'event async avec @Async
 *
 * PRINCIPE DDD : "Un événement = un FAIT passé" (Domain Event).
 * Le nom est au passé : "UserAdminEvent" = un événement d'administration.
 *
 * RELATION AVEC TaskCreatedEvent (déjà existant dans Core) :
 * ──────────────────────────────────────────────────────
 * TaskCreatedEvent : événement du domaine Core → écouté par Core
 * UserAdminEvent : événement du domaine IAM → écouté par Core
 * Les deux utilisent le même mécanisme (Spring ApplicationEvent).
 */
public class UserAdminEvent extends ApplicationEvent {

    /**
     * Type d'action administrative.
     * Utilisé par le listener Core pour déterminer quel ActivityLog.Action utiliser.
     */
    public enum AdminAction {
        ROLE_CHANGED,   // Rôle d'un utilisateur modifié
        USER_TOGGLED    // Utilisateur activé/désactivé
    }

    private final AdminAction action;
    private final String targetUsername;
    private final String actorEmail;
    private final String description;

    /**
     * @param source      L'objet qui publie l'événement (le contrôleur, "this")
     * @param action      Le type d'action (ROLE_CHANGED ou USER_TOGGLED)
     * @param targetUsername Le username de l'utilisateur ciblé
     * @param actorEmail  L'email de l'admin qui fait l'action
     * @param description Description humaine de l'action
     */
    public UserAdminEvent(Object source, AdminAction action,
                          String targetUsername, String actorEmail, String description) {
        super(source);
        this.action = action;
        this.targetUsername = targetUsername;
        this.actorEmail = actorEmail;
        this.description = description;
    }

    public AdminAction getAction() { return action; }
    public String getTargetUsername() { return targetUsername; }
    public String getActorEmail() { return actorEmail; }
    public String getDescription() { return description; }
}