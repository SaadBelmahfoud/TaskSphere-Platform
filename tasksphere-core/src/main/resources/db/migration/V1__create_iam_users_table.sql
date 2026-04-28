-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V1 : Table des utilisateurs IAM
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE : Cette table stocke les utilisateurs du système.
-- Elle correspond à l'entité JPA UserEntity dans tasksphere-iam.
--
-- CONVENTIONS PostgreSQL :
-- - UUID comme clé primaire (plus sûr que les auto-increment)
-- - Les contraintes NOT NULL et UNIQUE garantissent l'intégrité
-- - Les index sur email et username accélèrent les recherches
-- - Le type VARCHAR au lieu de TEXT pour les champs limités
--
-- RAPPEL : En PostgreSQL, les identifiants (noms de tables/colonnes)
-- sont insensibles à la casse sauf s'ils sont entre guillemets doubles.
-- On utilise les minuscules par convention.

CREATE TABLE IF NOT EXISTS iam_users (
                                         id              VARCHAR(36)    NOT NULL PRIMARY KEY,  -- UUID en format texte
    username        VARCHAR(50)    NOT NULL UNIQUE,        -- Nom d'utilisateur unique
    email           VARCHAR(255)   NOT NULL UNIQUE,        -- Email unique (RFC 5321: max 254)
    password        VARCHAR(255)   NOT NULL,               -- Hash BCrypt (60 chars)
    role            VARCHAR(20)    NOT NULL DEFAULT 'USER',-- USER, MANAGER, ADMIN
    first_name      VARCHAR(100),                          -- Prénom
    last_name       VARCHAR(100),                          -- Nom de famille
    avatar_url      VARCHAR(500),                          -- URL de l'avatar (optionnel)
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,  -- Compte actif/désactivé
    created_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    last_login      TIMESTAMP
    );

-- Index pour accélérer les recherches par email (utilisé par le login)
CREATE INDEX IF NOT EXISTS idx_iam_users_email ON iam_users(email);

-- Index pour accélérer les recherches par username
CREATE INDEX IF NOT EXISTS idx_iam_users_username ON iam_users(username);

-- Commentaire sur la table (documentation dans la base)
COMMENT ON TABLE iam_users IS 'Table des utilisateurs du système IAM';
COMMENT ON COLUMN iam_users.password IS 'Hash BCrypt du mot de passe (jamais en clair)';
COMMENT ON COLUMN iam_users.role IS 'Rôle utilisateur : USER, MANAGER ou ADMIN';