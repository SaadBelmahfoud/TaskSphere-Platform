-- ═══════════════════════════════════════════════════════════════════
-- MIGRATION V8 : Ajout de la contrainte FK comments.task_id → tasks.id
-- ═══════════════════════════════════════════════════════════════════
--
-- PHASE 2 — TÂCHE 5 : Intégrité référentielle comments ↔ tasks
-- ──────────────────────────────────────────────────────────────
--
-- PROBLÈME AVANT :
--   La table comments a une colonne task_id avec un INDEX,
--   mais PAS de contrainte FOREIGN KEY. Cela signifie :
--
--   1. ORPHELINS POSSIBLES :
--      On peut insérer un commentaire avec un task_id qui
--      ne correspond à AUCUNE tâche existante.
--      → Données incohérentes : un commentaire "fantôme"
--        référençant une tâche qui n'existe pas.
--
--   2. SUPPRESSION SANS PROTECTION :
--      On peut supprimer physiquement une tâche sans que
--      ses commentaires soient affectés.
--      → Commentaires orphelins référençant une tâche supprimée.
--
--   3. PAS DE GARANTIE BDD :
--      L'intégrité référentielle est vérifiée uniquement
--      côté application (Java). Si un bug ou une requête
--      SQL manuelle insère des données incohérentes, la BDD
--      ne peut pas les empêcher.
--
-- SOLUTION APRÈS :
--   Ajouter une FOREIGN KEY avec ON DELETE CASCADE :
--   - comments.task_id DOIT référencer un id existant dans tasks
--   - Si une tâche est physiquement supprimée → ses commentaires
--     sont automatiquement supprimés (CASCADE)
--
-- NOTE IMPORTANTE SUR LE SOFT DELETE :
-- ──────────────────────────────────────
-- TaskSphere utilise le soft delete pour les tâches :
--   UPDATE tasks SET deleted_at = NOW() WHERE id = ?
--
-- Le soft delete NE déclenche PAS le CASCADE car la ligne
-- n'est PAS physiquement supprimée de la table tasks.
-- Le CASCADE ne s'active QUE pour un DELETE physique :
--   DELETE FROM tasks WHERE id = ?
--
-- Cela signifie que :
-- - Quand une tâche est soft-deleted → les commentaires RESTENT
--   (la tâche existe toujours en base, juste marquée supprimée)
-- - Si on supprime physiquement une tâche → les commentaires sont CASCADE-supprimés
--
-- C'est le comportement souhaité car :
-- - Les commentaires d'une tâche soft-deleted peuvent être restaurés
-- - Les commentaires d'une tâche physiquement supprimée doivent disparaître
--
-- COMPATIBILITÉ AVEC LES DONNÉES EXISTANTES :
-- ────────────────────────────────────────────
-- AVANT d'ajouter la FK, on doit s'assurer qu'il n'y a PAS
-- de commentaires orphelins (task_id ne référençant aucune tâche).
-- Sinon, ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY échouera.
--
-- La requête de nettoyage ci-dessous supprime les commentaires orphelins.
-- En production, on devrait les archiver plutôt que les supprimer,
-- mais pour ce projet, la suppression directe est acceptable.
-- ═══════════════════════════════════════════════════════════════════

-- ÉTAPE 1 : Nettoyer les commentaires orphelins (task_id ne référençant aucune tâche)
-- Si aucun orphelin n'existe, cette requête ne fait rien (0 lignes supprimées).
-- Si des orphelins existent, ils sont supprimés pour permettre l'ajout de la FK.
DELETE FROM comments
WHERE task_id IS NOT NULL
  AND task_id NOT IN (SELECT id FROM tasks);

-- ÉTAPE 2 : Ajouter la contrainte FOREIGN KEY
-- ON DELETE CASCADE : si une tâche est physiquement supprimée,
-- ses commentaires le sont aussi automatiquement.
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_task_id
        FOREIGN KEY (task_id) REFERENCES tasks(id)
            ON DELETE CASCADE;

-- NOTE : L'index idx_comments_task_id créé en V4 est redondant
-- avec la FK (PostgreSQL crée automatiquement un index pour la FK),
-- mais on le garde pour la compatibilité et les performances de lecture.
-- L'index n'est PAS un problème : il accélère les JOIN et les requêtes
-- WHERE task_id = ? (utile pour getCommentsByTaskId).