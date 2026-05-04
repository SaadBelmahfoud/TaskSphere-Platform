-- ═══════════════════════════════════════════════════════════════════
-- PHASE 3 — FEATURE 6 : Table des pièces jointes
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE — STOCKAGE DÉDOUBLÉ (Double Storage) :
-- ┌──────────────────────────────────────────────────────────────┐
-- │  Table attachments (MÉTADONNÉES)                             │
-- │  → Nom du fichier, type MIME, taille, qui a uploadé, quand  │
-- │  → Clé de stockage (storageKey) pour retrouver le fichier   │
-- │                                                              │
-- │  File Storage (FICHIER BINAIRE)                              │
-- │  → Le fichier lui-même (disque local, MinIO, S3)            │
-- │  → Identifié par storageKey                                 │
-- └──────────────────────────────────────────────────────────────┘
--
-- Pourquoi séparer les métadonnées du fichier ?
-- 1. REQUÊTAGE : On peut lister/chercher les pièces jointes par tâche, type, etc.
-- 2. SÉCURITÉ : Le fichier n'est PAS servi directement (contrôle d'accès via API)
-- 3. PORTABILITÉ : On peut changer de stockage sans modifier les métadonnées
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE attachments (
                             id              VARCHAR(36)    NOT NULL PRIMARY KEY,
                             task_id         VARCHAR(36)    NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                             file_name       VARCHAR(255)   NOT NULL,          -- Nom original du fichier
                             content_type    VARCHAR(100)   NOT NULL,           -- Type MIME (image/png, application/pdf, etc.)
                             file_size       BIGINT         NOT NULL,            -- Taille en octets
                             storage_key     VARCHAR(500)   NOT NULL,            -- Clé dans le stockage (chemin/UUID.extension)
                             uploaded_by     VARCHAR(255)   NOT NULL,            -- Email de l'utilisateur qui a uploadé
                             uploaded_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attachments_task_id ON attachments(task_id);
CREATE INDEX idx_attachments_uploaded_by ON attachments(uploaded_by);

COMMENT ON TABLE attachments IS 'Pièces jointes des tâches (métadonnées, fichier binaire dans le stockage)';
COMMENT ON COLUMN attachments.storage_key IS 'Clé de stockage unique pour retrouver le fichier binaire (disque, MinIO, S3)';