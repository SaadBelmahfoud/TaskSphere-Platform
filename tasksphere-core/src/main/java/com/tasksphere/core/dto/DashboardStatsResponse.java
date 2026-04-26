package com.tasksphere.core.dto;

import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Statistiques du Dashboard
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU CONCEPT — AGRÉGATION POUR DASHBOARD :
 * ─────────────────────────────────────────
 * Ce DTO agrège des données provenant de PLUSIEURS sources :
 * - TaskRepository : comptes par statut, par priorité, créées cette semaine, etc.
 * - ActivityLogPort : activités récentes
 *
 * C'est le contrôleur (DashboardController) qui assemble ces données.
 * Le service DashboardService fait les calculs métier si nécessaire.
 *
 * CHAMPS :
 * ──────
 * - totalTasks : nombre total de tâches actives (non supprimées)
 * - tasksByStatus : Map {TODO: 10, DOING: 8, DONE: 7} → pour le PieChart
 * - tasksByPriority : Map {LOW: 5, MEDIUM: 10, HIGH: 7, CRITICAL: 3} → pour le BarChart
 * - recentActivities : 10 dernières activités → pour la timeline du Dashboard
 * - tasksCreatedThisWeek : compteur pour la carte stat
 * - tasksCompletedThisWeek : compteur pour la carte stat
 * - overdueTasks : tâches dont la dueDate est passée ET status ≠ DONE
 *
 * UTILISATION PAR LE FRONTEND :
 * Le hook useDashboardStatsQuery() appelle GET /api/v1/dashboard/stats
 * et reçoit ce DTO. Les composants Chart de recharts l'affichent.
 */
public record DashboardStatsResponse(
        long totalTasks,
        Map<String, Long> tasksByStatus,
        Map<String, Long> tasksByPriority,
        List<ActivityLogResponse> recentActivities,
        long tasksCreatedThisWeek,
        long tasksCompletedThisWeek,
        long overdueTasks
) {}