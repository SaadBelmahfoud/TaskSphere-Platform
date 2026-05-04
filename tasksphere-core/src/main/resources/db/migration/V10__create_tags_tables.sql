-- ═══════════════════════════════════════════════════════════════════
-- PHASE 3 — FEATURE 3 : Tables pour le système de tags/labels
-- ═══════════════════════════════════════════════════════════════════
--
-- PRINCIPE — MANY-TO-MANY avec table de jointure :
-- ┌────────────┐     ┌──────────────┐     ┌────────────┐
-- │   tasks    │ ←── │  task_tags   │ ──→ │   tags     │
-- │            │     │ (jointure)   │     │            │
-- │ id         │     │ task_id (FK) │     │ id         │
-- │ title      │     │ tag_id  (FK) │     │ name       │
-- │ ...        │     │              │     │ color      │
-- └────────────┘     └──────────────┘     │ created_by │
--                                          └────────────┘
--
-- CONTRAINTE D'UNICITÉ :
-- Un tag est unique par nom (insensible à la casse).
-- Deux utilisateurs ne peuvent pas créer le même tag.
-- Cela évite les doublons comme "Bug", "bug", "BUG".
--
-- COULEUR DES TAGS :
-- Chaque tag a une couleur hexadécimale (#RRGGBB) pour
-- l'affichage visuel dans le frontend. Couleur par défaut : #6B7280 (gris).
-- ═══════════════════════════════════════════════════════════════════

-- Table des tags
CREATE TABLE tags (
                      id          VARCHAR(36)    NOT NULL PRIMARY KEY,
                      name        VARCHAR(50)    NOT NULL,
                      color       VARCHAR(7)     NOT NULL DEFAULT '#6B7280',  -- Couleur hex (#RRGGBB)
                      created_by  VARCHAR(255)   NOT NULL,                     -- Email du créateur
                      created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Index d'unicité insensible à la casse sur le nom du tag
-- Le LOWER() garantit que "Bug" et "bug" sont considérés identiques
CREATE UNIQUE INDEX idx_tags_name_lower ON tags(LOWER(name));

-- Table de jointure Many-to-Many : tâches ↔ tags
CREATE TABLE task_tags (
                           task_id     VARCHAR(36)    NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                           tag_id      VARCHAR(36)    NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                           PRIMARY KEY (task_id, tag_id)  -- Clé composite : un tag ne peut être appliqué qu'une fois par tâche
);

-- Index pour trouver les tags d'une tâche
CREATE INDEX idx_task_tags_task_id ON task_tags(task_id);

-- Index pour trouver les tâches d'un tag
CREATE INDEX idx_task_tags_tag_id ON task_tags(tag_id);

COMMENT ON TABLE tags IS 'Tags/labels personnalisables pour catégoriser les tâches';
COMMENT ON TABLE task_tags IS 'Table de jointure Many-to-Many entre tâches et tags';
COMMENT ON COLUMN tags.color IS 'Couleur hexadécimale du tag (#RRGGBB) pour l''affichage frontend';