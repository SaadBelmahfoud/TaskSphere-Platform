-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V6 : Correction des types bytea → text
-- ═══════════════════════════════════════════════════════════════════
--
-- PROBLÈME : Les colonnes title et description de la table tasks
-- ont été créées en type BYTEA (binaire) au lieu de VARCHAR/TEXT.
-- Cela provoque l'erreur PostgreSQL :
--   "function lower(bytea) does not exist"
-- car la fonction LOWER() ne fonctionne pas sur le type bytea.
--
-- CAUSE : Hibernate ddl-auto=update a créé les colonnes avant
-- que Flyway ne soit configuré. Hibernate a mappé String → bytea
-- au lieu de String → varchar/text.
--
-- CONSÉQUENCE : La recherche de tâches (searchTasks) utilise
-- LOWER(title) LIKE '%keyword%' → ERREUR → la liste est vide.
-- Mais COUNT(*) fonctionne car il n'utilise pas LOWER().
--
-- SOLUTION : ALTER COLUMN pour convertir bytea → varchar/text
-- en utilisant la conversion explicite ::text
--
-- RÉSULTAT :
-- - LOWER(title) fonctionnera correctement
-- - Les tâches apparaîtront dans la liste
-- - Les données existantes sont préservées

ALTER TABLE tasks ALTER COLUMN title TYPE VARCHAR(255) USING title::text;
ALTER TABLE tasks ALTER COLUMN description TYPE TEXT USING description::text;
