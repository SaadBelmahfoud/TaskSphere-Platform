package com.tasksphere.core.controller;

import com.tasksphere.core.dto.DashboardStatsResponse;
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
}