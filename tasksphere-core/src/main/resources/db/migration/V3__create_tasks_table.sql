-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V3 : Table des tâches
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE : Cette table stocke toutes les tâches du système.
-- Elle correspond à l'entité JPA TaskEntity dans tasksphere-core.
--
-- SOFT DELETE : La colonne deleted_at permet de "supprimer" une tâche
-- sans la retirer de la base. Toutes les requêtes filtrent WHERE deleted_at IS NULL.
-- Cela permet de restaurer des tâches supprimées par erreur.

CREATE TABLE IF NOT EXISTS tasks (
                                     id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    title           VARCHAR(255)   NOT NULL,
    description     TEXT,                                    -- Texte long (pas de limite)
    status          VARCHAR(20)    NOT NULL DEFAULT 'TODO', -- TODO, DOING, DONE
    priority        VARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',-- LOW, MEDIUM, HIGH, CRITICAL
    due_date        DATE,                                    -- Date d'échéance
    completed_at    TIMESTAMP,                               -- Date de complétion
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP,                               -- Soft delete (NULL = actif)
    user_id         VARCHAR(255)   NOT NULL,                -- Email du créateur
    assignee_id     VARCHAR(255),                           -- Email de l'assigné

-- Contraintes de validation
    CONSTRAINT chk_tasks_status CHECK (status IN ('TODO', 'DOING', 'DONE')),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
    );

-- Index pour filtrer par statut (utilisé dans le dashboard et les listes)
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status) WHERE deleted_at IS NULL;

-- Index pour filtrer par créateur (RBAC : un USER ne voit que ses tâches)
CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks(user_id) WHERE deleted_at IS NULL;

-- Index pour filtrer par assigné
CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id ON tasks(assignee_id) WHERE deleted_at IS NULL;