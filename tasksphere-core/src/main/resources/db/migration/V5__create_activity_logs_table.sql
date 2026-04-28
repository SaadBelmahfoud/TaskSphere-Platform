-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V5 : Table du journal d'audit (Activity Log)
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE : Cette table est APPEND-ONLY (insertion uniquement).
-- On ne modifie ni ne supprime jamais un log d'audit.
-- Cela garantit la traçabilité complète de toutes les actions.

CREATE TABLE IF NOT EXISTS activity_logs (
                                             id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    action          VARCHAR(50)    NOT NULL,               -- CREATED, UPDATED, STATUS_CHANGED, etc.
    description     TEXT           NOT NULL,               -- Description humaine de l'action
    username        VARCHAR(100)   NOT NULL,               -- Email de l'utilisateur
    task_id         VARCHAR(36),                            -- ID de la tâche (nullable)
    task_title      VARCHAR(255),                           -- Titre dénormalisé (snapshot)
    timestamp       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Index pour filtrer par action (dashboard)
CREATE INDEX IF NOT EXISTS idx_activity_logs_action ON activity_logs(action);

-- Index pour filtrer par utilisateur
CREATE INDEX IF NOT EXISTS idx_activity_logs_username ON activity_logs(username);

-- Index pour filtrer par tâche
CREATE INDEX IF NOT EXISTS idx_activity_logs_task_id ON activity_logs(task_id);

-- Index pour trier par date (chronologique)
CREATE INDEX IF NOT EXISTS idx_activity_logs_timestamp ON activity_logs(timestamp DESC);