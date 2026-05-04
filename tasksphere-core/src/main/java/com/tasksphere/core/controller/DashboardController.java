package com.tasksphere.core.controller;

import com.tasksphere.core.dto.BurndownDataResponse;
import com.tasksphere.core.dto.DashboardStatsResponse;
import com.tasksphere.core.dto.VelocityDataResponse;
import com.tasksphere.core.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : DashboardController (API REST Dashboard)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ENDPOINT :
 * ──────────
 * GET /api/v1/dashboard/stats → DashboardStatsResponse
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 1 : Refactoring — Extraction vers DashboardService
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT :
 *   DashboardController contenait TOUTE la logique métier :
 *   - Calcul du startOfWeek
 *   - Extraction du rôle depuis Authentication
 *   - Appels directs à TaskPersistencePort et ActivityLogPort
 *   - Assemblage du DashboardStatsResponse
 *
 *   PROBLÈME : Violation du SRP (Single Responsibility Principle).
 *   Un contrôleur est un adaptateur d'entrée HTTP, pas un service métier.
 *   Il ne doit ni calculer, ni orchestrer — il délègue.
 *
 * APRÈS :
 *   DashboardController ne fait PLUS que :
 *   1. Recevoir l'Authentication (injectée par Spring Security)
 *   2. Appeler dashboardService.getDashboardStats(authentication)
 *   3. Retourner ResponseEntity.ok(response)
 *
 *   TOUTE la logique métier est dans DashboardService.
 *   Le contrôleur est maintenant un "thin controller" (contrôleur mince).
 *
 * PRINCIPE THIN CONTROLLER / FAT SERVICE :
 * ┌──────────────────────────────────────────────────────────────┐
 * │  CONTRÔLEUR (thin) :                                         │
 * │  - Reçoit la requête HTTP                                    │
 * │  - Extrait les paramètres bruts                              │
 * │  - Appelle le service                                        │
 * │  - Retourne la réponse HTTP                                  │
 * │  → AUCUNE logique métier                                     │
 * │                                                               │
 * │  SERVICE (fat) :                                              │
 * │  - Contient la logique métier                                │
 * │  - Orchestre les appels aux ports                            │
 * │  - Calcule, agrège, transforme                               │
 * │  → TOUTE la logique métier                                   │
 * └──────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 3 (ISP) : Injection de TaskDashboardPort
 * ═══════════════════════════════════════════════════════════════════
 * Le contrôleur n'injecte PLUS directement TaskPersistencePort.
 * C'est DashboardService qui injecte TaskDashboardPort (le port ISP-split).
 * Le contrôleur ne connaît QUE DashboardService → couplage minimal.
 * ═══════════════════════════════════════════════════════════════════
 *
 * CONNEXION FRONTEND ↔ BACKEND :
 * ─────────────────────────────────
 * Le hook useDashboard.ts appelle GET /dashboard/stats
 * Le composant dashboard/page.tsx utilise les données pour les charts recharts.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 1 : Remplacement des dépendances
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : Deux ports injectés directement dans le contrôleur
     *   private final TaskPersistencePort taskPersistencePort;
     *   private final ActivityLogPort activityLogPort;
     *
     * APRÈS : Un seul service injecté (Facade Pattern)
     *   private final DashboardService dashboardService;
     *
     * AVANTAGE : Le contrôleur ne connaît ni les ports, ni les détails
     * d'implémentation. Si le DashboardService change ses dépendances
     * internes (ajoute un port, en retire un), le contrôleur n'est PAS impacté.
     * C'est le principe de l'encapsulation : le service cache les détails.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final DashboardService dashboardService;

    /**
     * GET /api/v1/dashboard/stats
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 1 : Méthode simplifiée
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (15 lignes de logique dans le contrôleur) :
     *   - Extraction du rôle
     *   - Calcul du startOfWeek
     *   - 7 appels aux ports
     *   - Assemblage du DTO
     *
     * APRÈS (1 ligne — délégation au service) :
     *   - Appel à dashboardService.getDashboardStats(authentication)
     *   - Le service retourne le DTO prêt à l'emploi
     *
     * PRINCIPE : Le contrôleur est un "pass-through" (relais).
     * Il ne fait que recevoir et transmettre — aucune logique.
     * ═══════════════════════════════════════════════════════════════════
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            Authentication authentication) {

        log.info("CONTROLLER : GET /dashboard/stats — {}", authentication.getName());

        // DÉLÉGATION TOTALE au service — thin controller pattern
        DashboardStatsResponse response = dashboardService.getDashboardStats(authentication);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dashboard/burndown?weeks=4
     *
     * PHASE 3 — FEATURE 4 : Données du Burndown Chart
     * ──────────────────────────────────────────────────
     * Retourne les données idéales et réelles pour le burndown chart.
     * La période par défaut est 4 semaines.
     */
    @GetMapping("/burndown")
    public ResponseEntity<BurndownDataResponse> getBurndownData(
            Authentication authentication,
            @RequestParam(defaultValue = "4") int weeks) {

        log.info("CONTROLLER : GET /dashboard/burndown — {} (weeks: {})",
                authentication.getName(), weeks);

        if (weeks < 1) weeks = 4;
        if (weeks > 12) weeks = 12;

        BurndownDataResponse response = dashboardService.getBurndownData(authentication, weeks);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dashboard/velocity?weeks=8
     *
     * PHASE 3 — FEATURE 4 : Données de vélocité
     * ──────────────────────────────────────────────────
     * Retourne le nombre de tâches complétées par semaine
     * et la vélocité moyenne.
     */
    @GetMapping("/velocity")
    public ResponseEntity<VelocityDataResponse> getVelocityData(
            Authentication authentication,
            @RequestParam(defaultValue = "8") int weeks) {

        log.info("CONTROLLER : GET /dashboard/velocity — {} (weeks: {})",
                authentication.getName(), weeks);

        if (weeks < 1) weeks = 8;
        if (weeks > 24) weeks = 24;

        VelocityDataResponse response = dashboardService.getVelocityData(authentication, weeks);
        return ResponseEntity.ok(response);
    }
}