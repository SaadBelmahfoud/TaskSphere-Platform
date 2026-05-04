package com.tasksphere.core.dto;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Données de vélocité
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 4 : Dashboard analytique avancé
 * ──────────────────────────────────────────────────
 *
 * PRINCIPE — VÉLOCITÉ :
 * ───────────────────────
 * La vélocité mesure le nombre de tâches complétées par période.
 * C'est la métrique clé pour la planification agile.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Tâches complétées                                         │
 * │   8 ┤         ████                                         │
 * │   7 ┤   ████  ████                                         │
 * │   6 ┤   ████  ████  ████                                   │
 * │   5 ┤   ████  ████  ████  ████                             │
 * │   4 ┤   ████  ████  ████  ████                             │
 * │     └───┬─────┬─────┬─────┬──→ Semaines                    │
 * │         S1    S2    S3    S4                                │
 * │                                                            │
 * │  Vélocité moyenne : (5+7+6+5)/4 = 5.75 tâches/semaine    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * STRUCTURE :
 * - weeklyVelocity : liste de (semaine, nombre de tâches complétées)
 * - averageVelocity : vélocité moyenne sur la période
 * - periodWeeks : nombre de semaines dans la période
 */
public record VelocityDataResponse(
        List<WeeklyData> weeklyVelocity,
        double averageVelocity,
        int periodWeeks
) {

    /**
     * Données hebdomadaires pour le graphique de vélocité.
     *
     * @param weekLabel  Label de la semaine (ex: "S1", "2024-W03")
     * @param completed  Nombre de tâches complétées cette semaine
     */
    public record WeeklyData(String weekLabel, long completed) {}
}