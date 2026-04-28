-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V2 : Table des refresh tokens
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE : Les refresh tokens sont stockés en base pour permettre :
-- - La révocation individuelle (logout)
-- - La révocation de masse (déconnexion de toutes les sessions)
-- - La rotation (un nouveau token est créé à chaque refresh)
-- - La détection de vol (si un token révoqué est réutilisé)
--
-- RELATION : Un refresh token appartient à UN utilisateur (@ManyToOne).
-- Si l'utilisateur est supprimé, ses tokens le sont aussi (CASCADE).

CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id              VARCHAR(36)    NOT NULL PRIMARY KEY,
    token           VARCHAR(255)   NOT NULL UNIQUE,        -- Token hashé (BCrypt)
    user_id         VARCHAR(36)    NOT NULL,               -- FK vers iam_users.id
    expires_at      TIMESTAMP      NOT NULL,               -- Date d'expiration (7j)
    revoked         BOOLEAN        NOT NULL DEFAULT FALSE,  -- Token révoqué ?
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Clé étrangère vers iam_users
    CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id)
    REFERENCES iam_users(id)
    ON DELETE CASCADE  -- Si l'utilisateur est supprimé, ses tokens aussi
    );

-- Index pour retrouver rapidement un token (utilisé à chaque refresh)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);

-- Index pour retrouver tous les tokens d'un utilisateur (utilisé au logout)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);