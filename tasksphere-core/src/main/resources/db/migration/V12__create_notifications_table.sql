-- ═══════════════════════════════════════════════════════════════════
-- PHASE 3 — FEATURE 1 : Table des notifications persistées
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE — NOTIFICATIONS PERSISTÉES :
-- Les notifications sont stockées en base pour permettre :
-- 1. La récupération des notifications manquées lors de la reconnexion
-- 2. Le comptage des notifications non lues (badge)
-- 3. Le marquage comme lu/supprimé
--
-- ARCHITECTURE :
-- ┌──────────────────────────────────────────────────────────────────┐
-- │  Événement → TaskAuditEventListener                             │
-- │    → NotificationService.notifyUser()                           │
-- │      → 1. Persist in notifications table (NEW)                  │
-- │      → 2. Push via WebSocket (existing)                         │
-- │                                                                 │
-- │  REST API :                                                     │
-- │    GET /api/v1/notifications          → Liste des notifs        │
-- │    GET /api/v1/notifications/unread-count → Compteur non lues   │
-- │    PATCH /api/v1/notifications/{id}/read → Marquer comme lu     │
-- │    POST /api/v1/notifications/read-all  → Tout marquer comme lu │
-- └──────────────────────────────────────────────────────────────────┘
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE notifications (
                               id                  VARCHAR(36)    NOT NULL PRIMARY KEY,
                               type                VARCHAR(20)    NOT NULL DEFAULT 'INFO',   -- INFO, WARNING, URGENT
                               title               VARCHAR(255)   NOT NULL,                   -- Titre court
                               message             TEXT           NOT NULL,                    -- Description détaillée
                               actor_username      VARCHAR(255),                               -- Utilisateur qui a déclenché l'action
                               target_username     VARCHAR(255)   NOT NULL,                   -- Destinataire de la notification
                               task_id             VARCHAR(36),                                -- ID de la tâche concernée (optionnel)
                               task_title          VARCHAR(255),                               -- Titre de la tâche (denormalized)
                               is_read             BOOLEAN       NOT NULL DEFAULT FALSE,       -- Notification lue ou non
                               created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Index pour trouver les notifications d'un utilisateur (triées par date)
CREATE INDEX idx_notifications_target_username ON notifications(target_username, created_at DESC);

-- Index pour compter les notifications non lues
CREATE INDEX idx_notifications_unread ON notifications(target_username, is_read) WHERE is_read = FALSE;

-- Index pour trouver les notifications d'une tâche
CREATE INDEX idx_notifications_task_id ON notifications(task_id);

COMMENT ON TABLE notifications IS 'Notifications persistées pour les utilisateurs (complémentaire au WebSocket)';
COMMENT ON COLUMN notifications.type IS 'Type de notification : INFO, WARNING, URGENT';
COMMENT ON COLUMN notifications.actor_username IS 'Email de lutilisateur qui a déclenché laction (peut être null pour les événements système)';
COMMENT ON COLUMN notifications.target_username IS 'Email du destinataire de la notification';
COMMENT ON COLUMN notifications.is_read IS 'Indique si la notification a été lue par le destinataire';