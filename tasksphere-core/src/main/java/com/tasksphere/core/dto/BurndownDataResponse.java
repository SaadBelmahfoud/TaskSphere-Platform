package com.tasksphere.core.dto;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Données du Burndown Chart
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 4 : Dashboard analytique avancé
 * ──────────────────────────────────────────────────
 *
 * PRINCIPE — BURNDOWN CHART :
 * ────────────────────────────
 * Le burndown chart montre l'évolution du travail restant jour par jour.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Tâches                                                    │
 * │  20 ┤ *                                                    │
 * │     │   *   ← Ligne idéale (droite)                        │
 * │  15 ┤     *                                                │
 * │     │       *                                              │
 * │  10 ┤         *                                            │
 * │     │           *   ← Ligne réelle (courbe)                │
 * │   5 ┤             *  *                                     │
 * │     │                 * *                                  │
 * │   0 ┤                   * ← Objectif : 0 tâches restantes │
 * │     └──┬──┬──┬──┬──┬──┬──┬──→ Jours                      │
 * │        J1 J2 J3 J4 J5 J6 J7                               │
 * └─────────────────────────────────────────────────────────────┘
 *
 * STRUCTURE :
 * - idealLine   : liste de points (jour, nombre idéal de tâches restantes)
 * - actualLine  : liste de points (jour, nombre réel de tâches restantes)
 * - totalTasks  : nombre total de tâches au début de la période
 * - startDate   : date de début de la période
 * - endDate     : date de fin de la période
 */
public record BurndownDataResponse(
        List<DataPoint> idealLine,
        List<DataPoint> actualLine,
        long totalTasks,
        String startDate,
        String endDate
) {

    /**
     * Point de données pour le graphique.
     *
     * @param date  La date (format yyyy-MM-dd)
     * @param value Le nombre de tâches restantes à cette date
     */
    public record DataPoint(String date, long value) {}
}