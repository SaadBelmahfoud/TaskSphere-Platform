-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V4 : Table des commentaires
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS comments (
                                        id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    content         TEXT           NOT NULL,                -- Contenu du commentaire
    username        VARCHAR(100)   NOT NULL,               -- Email de l'auteur
    task_id         VARCHAR(36)    NOT NULL,               -- ID de la tâche
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Index pour retrouver les commentaires d'une tâche
CREATE INDEX IF NOT EXISTS idx_comments_task_id ON comments(task_id);

-- Index pour retrouver les commentaires d'un utilisateur
CREATE INDEX IF NOT EXISTS idx_comments_username ON comments(username);