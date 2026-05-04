-- ═══════════════════════════════════════════════════════════════════
-- PHASE 3 — FEATURE 2 : Table task_change_logs
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE : Enregistrer chaque modification de champ d'une tâche
-- avec les valeurs avant/après pour un audit trail détaillé.
--
-- DIFFÉRENCE AVEC activity_logs :
-- ┌──────────────────────────────────────────────────────────────┐
-- │  activity_logs          │ task_change_logs                    │
-- │  (Action globale)       │ (Champ par champ)                  │
-- │  "Tâche modifiée"       │ "priority: MEDIUM → HIGH"          │
-- │  "Statut changé"        │ "status: TODO → DOING"             │
-- │  1 entrée par action    │ N entrées par action (1 par champ) │
-- └──────────────────────────────────────────────────────────────┘
--
-- RELATION : task_change_logs.task_id → tasks.id
-- Un changement appartient toujours à une tâche.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE task_change_logs (
                                  id              VARCHAR(36)    NOT NULL PRIMARY KEY,
                                  task_id         VARCHAR(36)    NOT NULL REFERENCES tasks(id),
                                  field_name      VARCHAR(50)    NOT NULL,    -- Nom du champ modifié (title, status, priority, etc.)
                                  old_value       TEXT,                        -- Valeur avant modification (null si création)
                                  new_value       TEXT,                        -- Valeur après modification
                                  changed_by      VARCHAR(255)   NOT NULL,    -- Email de l'utilisateur qui a fait la modification
                                  changed_at      TIMESTAMP      NOT NULL DEFAULT NOW()  -- Date/heure de la modification
);

-- INDEX pour accélérer les requêtes par tâche
-- Le frontend va souvent chercher l'historique d'une tâche spécifique
-- via GET /api/v1/tasks/{id}/changes
CREATE INDEX idx_change_logs_task_id ON task_change_logs(task_id);

-- INDEX pour accélérer les requêtes par date (tri par plus récent)
CREATE INDEX idx_change_logs_changed_at ON task_change_logs(changed_at DESC);

-- COMMENTAIRE SUR LA TABLE
COMMENT ON TABLE task_change_logs IS 'Journal des modifications champ par champ des tâches (audit trail détaillé)';
COMMENT ON COLUMN task_change_logs.field_name IS 'Nom du champ modifié : title, description, status, priority, dueDate, assigneeId';
COMMENT ON COLUMN task_change_logs.old_value IS 'Valeur avant modification (null si le champ n''existait pas avant, ex: création)';
COMMENT ON COLUMN task_change_logs.new_value IS 'Valeur après modification';