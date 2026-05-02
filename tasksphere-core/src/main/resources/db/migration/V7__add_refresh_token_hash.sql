-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V7 : Ajout de la colonne token_hash pour lookup O(1)
-- ═══════════════════════════════════════════════════════════════════
--
-- PHASE 1 — CORRECTION P0-2 : RefreshTokenService O(N) → O(1)
--
-- PROBLÈME :
--   RefreshTokenService.verifyRefreshToken() charge TOUS les tokens
--   actifs (findByRevokedFalse()) puis itère avec BCrypt.matches().
--   Complexité : O(N) × coût BCrypt (~100ms) = CATASTROPHIQUE
--   → Avec 100 tokens : ~10 secondes par refresh
--   → Avec 1000 tokens : ~100 secondes par refresh
--
-- SOLUTION : Double hash (SHA-256 + BCrypt)
--   1. On stocke un hash SHA-256 du token brut dans une nouvelle colonne
--   2. SHA-256 est DÉTERMINISTE : même token → même hash → INDEXABLE
--   3. Au refresh : on calcule SHA-256(rawToken) → SELECT WHERE token_hash = ?
--   4. On obtient EXACTEMENT 1 token → BCrypt.matches() UNE SEULE FOIS
--   5. Complexité : O(1) × coût BCrypt (~100ms) = ACCEPTABLE
--
-- POURQUOI SHA-256 ET PAS BCRYPT POUR L'INDEX ?
--   BCrypt utilise un sel aléatoire (random salt) à chaque hachage.
--   Le même input produit TOUJOURS un hash BCrypt différent.
--   → Impossible de faire un SELECT WHERE token = ? avec BCrypt
--   SHA-256 est déterministe : même input → même hash → indexable
--
-- SÉCURITÉ DU HASH SHA-256 :
--   Le hash SHA-256 est une fonction à sens unique (one-way).
--   Même si un attaquant obtient le token_hash, il NE PEUT PAS
--   retrouver le token original (UUID 128 bits → espace trop grand).
--   De plus, le token brut n'est JAMAIS stocké en base.
--
-- COLONNE token_hash :
--   VARCHAR(64) car SHA-256 produit toujours un hex string de 64 caractères
--   NOT NULL car chaque token DOIT avoir un hash
--   UNIQUE car deux tokens différents ne peuvent pas avoir le même hash SHA-256
-- ═══════════════════════════════════════════════════════════════════

-- Ajouter la colonne token_hash
ALTER TABLE refresh_tokens ADD COLUMN token_hash VARCHAR(64);

-- Créer un index UNIQUE pour le lookup O(1)
-- UNIQUE car SHA-256 est une injection (pas de collision pratique)
CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- NOTE : On ne peut PAS mettre NOT NULL immédiatement car les tokens
-- existants n'ont pas de token_hash. Le service devra peupler cette
-- colonne progressivement. Les anciens tokens sans hash seront
-- toujours trouvés par l'ancienne méthode (findByRevokedFalse)
-- jusqu'à leur expiration naturelle (7 jours max).