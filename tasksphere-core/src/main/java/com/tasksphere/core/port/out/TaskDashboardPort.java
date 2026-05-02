package com.tasksphere.core.port.out;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Statistiques du Dashboard (Contrat du domaine)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 2 — TÂCHE 3 : Split ISP de TaskPersistencePort
 * ──────────────────────────────────────────────────────
 *
 * PRINCIPE ISP (Interface Segregation Principle) :
 * ──────────────────────────────────────────────────
 * "Les clients ne doivent pas être forcés de dépendre d'interfaces
 *  qu'ils n'utilisent pas." — Robert C. Martin (Uncle Bob)
 *
 * PROBLÈME AVANT :
 *   TaskPersistencePort contenait 20+ méthodes :
 *   - CRUD : save, findById, findByUserId, softDelete, etc.
 *   - Recherche : searchTasks, searchTasksForUser
 *   - Comptage : countActiveTasks, countByStatus, countByPriority, etc.
 *
 *   DashboardService (et DashboardController avant lui) injectait
 *   TaskPersistencePort mais n'utilisait que les 6 méthodes de comptage.
 *   → Il était forcé de dépendre de 14+ méthodes qu'il n'utilisait PAS.
 *   → Violation de l'ISP.
 *
 *   CONSÉQUENCES DE LA VIOLATION ISP :
 *   1. COUPLAGE EXCESSIF : DashboardService dépend de méthodes
 *      qu'il n'utilise pas. Si une méthode CRUD change de signature,
 *      DashboardService est impacté (recompilation inutile).
 *
 *   2. FAUSSE INSTABILITÉ : Un changement dans une méthode CRUD
 *      force la recompilation de DashboardService, même s'il ne
 *      l'utilise pas. C'est du "spurious coupling" (couplage artificiel).
 *
 *   3. TESTABILITÉ : Pour tester DashboardService, on doit mocker
 *      TaskPersistencePort avec ses 20+ méthodes, alors qu'on n'en
 *      utilise que 6. C'est fastidieux et source d'erreurs.
 *
 * SOLUTION APRÈS :
 *   Split en DEUX ports :
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  TaskPersistencePort (CRUD + Recherche)                         │
 *   │  → save, findById, findByUserId, softDelete,                    │
 *   │    searchTasks, searchTasksForUser                              │
 *   │  → Utilisé par : TaskManager, TaskController                    │
 *   │                                                                  │
 *   │  TaskDashboardPort (Comptage RBAC-aware)     ← CE FICHIER       │
 *   │  → countActiveTasks, countByStatus, countByPriority,            │
 *   │    countCreatedAfter, countCompletedAfter, countOverdueTasks    │
 *   │  → Utilisé par : DashboardService                               │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 *   CHAQUE CLIENT N'INJECTE QUE LE PORT DONT IL A BESOIN :
 *   - TaskManager injecte TaskPersistencePort (CRUD)
 *   - DashboardService injecte TaskDashboardPort (comptage)
 *   - Aucun client n'est forcé de dépendre de méthodes inutiles
 *
 * IMPLÉMENTATION PARTAGÉE :
 * ──────────────────────────
 * TaskPersistenceAdapter implémente LES DEUX interfaces :
 *   public class TaskPersistenceAdapter
 *       implements TaskPersistencePort, TaskDashboardPort { ... }
 *
 * C'est tout à fait valide en Java (une classe peut implémenter
 * plusieurs interfaces). L'adaptateur a UNE implémentation physique
 * mais DEUX contrats logiques.
 *
 * MÉTHODES RBAC-AWARE :
 * ─────────────────────
 * Toutes les méthodes de ce port acceptent un paramètre `username`
 * nullable qui permet de filtrer les résultats selon le rôle :
 *
 * - ADMIN/MANAGER : username = null → vue globale (pas de filtre)
 * - USER : username = "email" → filtrer par créateur OU assignataire
 *
 * PRINCIPE DU FILTRAGE OPTIONNEL EN JPQL :
 * La clause ":username IS NULL OR condition" est un pattern classique
 * en JPQL qui permet d'ignorer un filtre quand le paramètre est null.
 *
 * Si username = null  : "NULL IS NULL" = TRUE  → court-circuit, filtre ignoré
 * Si username = "x"  : "NULL IS NULL" = FALSE → on évalue la condition
 */
public interface TaskDashboardPort {

    /**
     * Compte les tâches actives, avec filtre RBAC optionnel.
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countActiveTasks(null)  → toutes les tâches actives
     * - USER          : countActiveTasks("email") → tâches créées OU assignées à email
     *
     * SQL GÉNÉRÉ (username = null) :
     * SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     *
     * SQL GÉNÉRÉ (username = "email") :
     * SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     *   AND (t.user_id = 'email' OR t.assignee_id = 'email')
     *
     * @param username L'email de l'utilisateur (null = pas de filtre, vue globale)
     * @return Le nombre de tâches actives (filtrées ou non selon le rôle)
     */
    long countActiveTasks(String username);

    /**
     * Compte les tâches actives par statut, avec filtre RBAC optionnel.
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countByStatus(null)  → { "TODO": 10, "DOING": 8, "DONE": 7 }
     * - USER          : countByStatus("email") → stats de l'utilisateur uniquement
     *
     * SQL GÉNÉRÉ :
     * SELECT t.status, COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     * GROUP BY t.status
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Map { "TODO": N, "DOING": N, "DONE": N }
     */
    Map<String, Long> countByStatus(String username);

    /**
     * Compte les tâches actives par priorité, avec filtre RBAC optionnel.
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countByPriority(null)  → { "LOW": 5, "MEDIUM": 12, ... }
     * - USER          : countByPriority("email") → stats de l'utilisateur uniquement
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Map { "LOW": N, "MEDIUM": N, "HIGH": N, "CRITICAL": N }
     */
    Map<String, Long> countByPriority(String username);

    /**
     * Compte les tâches actives créées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Tâches créées cette semaine"
     * - startOfWeek = lundi de la semaine en cours à minuit
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @param after    La date/heure de référence (créées après cette date)
     * @return Le nombre de tâches créées après la date spécifiée
     */
    long countCreatedAfter(String username, LocalDateTime after);

    /**
     * Compte les tâches actives complétées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Tâches terminées cette semaine"
     *
     * NOTE : completedAt est non-null UNIQUEMENT quand status = DONE.
     * Cette méthode compte implicitement les tâches DONE terminées après la date.
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @param after    La date/heure de référence (complétées après cette date)
     * @return Le nombre de tâches complétées après la date spécifiée
     */
    long countCompletedAfter(String username, LocalDateTime after);

    /**
     * Compte les tâches en retard, avec filtre RBAC optionnel.
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < NOW() → la date d'échéance est dépassée
     * - status ≠ DONE → la tâche n'est pas encore terminée
     * - deletedAt IS NULL → la tâche est active (non archivée)
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Le nombre de tâches en retard (filtrées ou non selon le rôle)
     */
    long countOverdueTasks(String username);
}