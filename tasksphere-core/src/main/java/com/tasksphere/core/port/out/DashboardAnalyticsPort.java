package com.tasksphere.core.port.out;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Données analytiques du Dashboard
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 4 : Port pour les métriques analytiques avancées
 * ──────────────────────────────────────────────────
 *
 * PRINCIPE ISP : Ce port est séparé de TaskDashboardPort car il
 * traite des données temporelles (burndown, vélocité) et non des
 * simples compteurs. Le client (DashboardService) n'injecte que
 * ce dont il a besoin.
 */
public interface DashboardAnalyticsPort {

    /**
     * Compte les tâches encore non-terminées (status ≠ DONE) à une date donnée.
     * Utilisé pour construire la ligne "réelle" du burndown chart.
     *
     * PRINCIPE : On compte les tâches créées AVANT la date qui ne sont
     * PAS encore terminées à cette date (completedAt > date OU completedAt IS NULL).
     *
     * @param username L'email (null = vue globale ADMIN/MANAGER)
     * @param date     La date de référence
     * @return Le nombre de tâches restantes à cette date
     */
    long countRemainingTasks(String username, LocalDateTime date);

    /**
     * Compte les tâches complétées dans un intervalle de temps.
     * Utilisé pour construire le graphique de vélocité.
     *
     * @param username L'email (null = vue globale ADMIN/MANAGER)
     * @param from     Début de l'intervalle
     * @param to       Fin de l'intervalle
     * @return Le nombre de tâches complétées dans l'intervalle
     */
    long countCompletedBetween(String username, LocalDateTime from, LocalDateTime to);

    /**
     * Compte les tâches créées avant une date (toujours actives).
     * Utilisé pour le total de départ du burndown.
     *
     * @param username L'email (null = vue globale)
     * @param before   Date limite de création
     * @return Nombre de tâches créées avant cette date et encore actives
     */
    long countCreatedBefore(String username, LocalDateTime before);
}