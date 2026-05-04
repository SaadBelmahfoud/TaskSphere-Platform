package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : TaskChangeLog (Historique des changements détaillé)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : Audit trail champ par champ
 * ─────────────────────────────────────────────────────
 *
 * PRINCIPE — CHANGE LOG vs ACTIVITY LOG :
 * ────────────────────────────────────────
 * ActivityLog : enregistre l'ACTION globale ("Tâche modifiée")
 * TaskChangeLog : enregistre chaque CHAMP modifié ("priority: MEDIUM → HIGH")
 *
 * EXEMPLE :
 * L'utilisateur modifie une tâche (titre + priorité) →
 *   ActivityLog   : 1 entrée "Tâche modifiée — titre changé, priorité → HIGH"
 *   TaskChangeLog : 2 entrées :
 *     1. field_name=title, old_value="Ancien titre", new_value="Nouveau titre"
 *     2. field_name=priority, old_value="MEDIUM", new_value="HIGH"
 *
 * POURQUOI SÉPARER LES DEUX CONCEPTS ?
 * ───────────────────────────────────────
 * 1. SRP : ActivityLog = journal d'activité global, TaskChangeLog = historique détaillé
 * 2. REQUÊTAGE : Le frontend a besoin de l'historique par tâche, pas des actions globales
 * 3. GRANULARITÉ : ActivityLog est "grossier", TaskChangeLog est "fin"
 * 4. UTILISATION UI : Le change log permet un diff visuel (avant → après)
 *
 * CHAMPS SUIVIS :
 * ────────────────
 * - title       : titre de la tâche
 * - description : description
 * - status      : TODO / DOING / DONE
 * - priority    : LOW / MEDIUM / HIGH / CRITICAL
 * - dueDate     : date d'échéance
 * - assigneeId  : email de l'assignataire
 *
 * PROPRIÉTÉ APPEND-ONLY :
 * ────────────────────────
 * Comme ActivityLog, les changements sont immuables.
 * On ne modifie JAMAIS un TaskChangeLog existant.
 */
public record TaskChangeLog(
        String id,              // UUID unique
        String taskId,          // ID de la tâche concernée
        String fieldName,       // Nom du champ modifié
        String oldValue,        // Valeur avant (null si création)
        String newValue,        // Valeur après
        String changedBy,       // Email de l'utilisateur qui a modifié
        LocalDateTime changedAt // Date/heure de la modification
) {

    /**
     * Factory Method : Crée une entrée de changement.
     *
     * @param taskId    ID de la tâche modifiée
     * @param fieldName Nom du champ (title, status, priority, etc.)
     * @param oldValue  Valeur avant (null si le champ n'existait pas)
     * @param newValue  Valeur après
     * @param changedBy Email de l'utilisateur qui a modifié
     * @return Une nouvelle instance TaskChangeLog
     */
    public static TaskChangeLog create(String taskId, String fieldName,
                                       String oldValue, String newValue,
                                       String changedBy) {
        return new TaskChangeLog(
                UUID.randomUUID().toString(),
                taskId,
                fieldName,
                oldValue,
                newValue,
                changedBy,
                LocalDateTime.now()
        );
    }
}