package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.dto.ActivityLogResponse;
import com.tasksphere.core.dto.DashboardStatsResponse;
import com.tasksphere.core.port.out.ActivityLogPort;
import com.tasksphere.core.port.out.TaskDashboardPort;
import com.tasksphere.core.dto.BurndownDataResponse;
import com.tasksphere.core.dto.VelocityDataResponse;
import com.tasksphere.core.port.out.DashboardAnalyticsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE D'APPLICATION : DashboardService
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 2 — TÂCHE 1 : Extraction de la logique Dashboard depuis DashboardController
 * ─────────────────────────────────────────────────────────────────
 *
 * PROBLÈME AVANT :
 *   DashboardController contenait de la LOGIQUE MÉTIER :
 *   - Calcul du startOfWeek
 *   - Extraction du rôle depuis Authentication
 *   - Détermination isAdminOrManager
 *   - Assemblage du DashboardStatsResponse
 *
 *   PRINCIPE VIOLÉ : SRP (Single Responsibility Principle)
 *   Un contrôleur est un ADAPTATEUR D'ENTRÉE HTTP. Son rôle est :
 *   1. Recevoir la requête HTTP
 *   2. Extraire les paramètres bruts
 *   3. Appeler le service métier
 *   4. Retourner la réponse HTTP
 *   Il ne doit PAS contenir de logique de calcul.
 *
 * SOLUTION APRÈS :
 *   DashboardService encapsule TOUTE la logique métier du dashboard :
 *   - RBAC : détermine le contexte utilisateur (global vs personnel)
 *   - Agrégation : collecte les stats depuis les ports
 *   - Calcul : détermine la semaine en cours
 *   - Assemblage : construit le DashboardStatsResponse
 *
 *   DashboardController ne fait PLUS que :
 *   1. Recevoir Authentication (injecté par Spring Security)
 *   2. Appeler dashboardService.getDashboardStats(authentication)
 *   3. Retourner ResponseEntity.ok(response)
 *
 * PRINCIPE DDD — APPLICATION SERVICE :
 * ─────────────────────────────────
 * Ce service est un "Application Service" (service d'application).
 * Il ne contient PAS de règles métier complexes — il ORCHESTRE
 * les appels aux ports pour construire une vue agrégée.
 *
 * C'est le pattern "Facade" : le contrôleur appelle UNE méthode
 * du service qui coordonne PLUSIEURS appels aux ports.
 *
 * DÉPENDANCES (ports injectés) :
 * ──────────────────────────────
 * 1. TaskDashboardPort : port de statistiques RBAC-aware pour les tâches
 *    → Méthodes : countActiveTasks, countByStatus, countByPriority,
 *      countCreatedAfter, countCompletedAfter, countOverdueTasks
 *    → Ce port est le RÉSULTAT du split ISP (Tâche 3)
 *
 * 2. ActivityLogPort : port de lecture des activités récentes
 *    → Méthode : findRecent(limit)
 *
 * TRANSACTION :
 * ─────────────
 * @Transactional(readOnly = true) car le dashboard ne fait que LIRE des données.
 * L'optimisation readOnly permet à Hibernate de :
 * - Désactiver le dirty checking (pas de snapshot à comparer)
 * - Utiliser une connexion en lecture seule (pas de verrouillage)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 3 (ISP) : Injection de TaskDashboardPort
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (Tâche 3) : TaskPersistencePort était une interface "fourre-tout"
     * contenant 20+ méthodes (CRUD + Recherche + Dashboard).
     * Le DashboardController injectait TaskPersistencePort juste pour
     * les 6 méthodes de comptage → violation de l'ISP.
     *
     * APRÈS (Tâche 3) : TaskDashboardPort contient UNIQUEMENT les méthodes
     * de comptage RBAC-aware. DashboardService n'injecte QUE ce dont
     * il a besoin → interface minimale = couplage réduit.
     *
     * Voir la Tâche 3 pour le détail du split ISP.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final TaskDashboardPort taskDashboardPort;

    /**
     * Port de lecture des activités récentes (audit log).
     * Utilisé pour afficher les 10 dernières actions sur le Dashboard.
     */
    private final ActivityLogPort activityLogPort;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * MÉTHODE PRINCIPALE : getDashboardStats
     * ═══════════════════════════════════════════════════════════════════
     *
     * Orchestre la collecte de TOUTES les statistiques du Dashboard
     * en fonction du rôle de l'utilisateur connecté.
     *
     * RBAC LOGIQUE :
     * ┌────────────────────────────────────────────────────────────────┐
     * │  ADMIN / MANAGER : username = null                             │
     * │  → Les méthodes countActiveTasks(null) retournent les stats   │
     * │    GLOBALES (toutes les tâches de tous les utilisateurs)      │
     * │                                                                │
     * │  USER : username = "email@x.com"                              │
     * │  → Les méthodes countActiveTasks(email) retournent les stats  │
     * │    PERSONNELLES (tâches créées + tâches assignées)            │
     * └────────────────────────────────────────────────────────────────┘
     *
     * DONNÉES AGRÉGÉES (7 KPI) :
     * ──────────────────────────
     * 1. totalTasks : compteur global de tâches actives
     * 2. tasksByStatus : Map {TODO: N, DOING: N, DONE: N} → PieChart
     * 3. tasksByPriority : Map {LOW: N, MEDIUM: N, HIGH: N, CRITICAL: N} → BarChart
     * 4. recentActivities : 10 dernières actions → Timeline
     * 5. tasksCreatedThisWeek : compteur de la semaine → Card
     * 6. tasksCompletedThisWeek : compteur de la semaine → Card
     * 7. overdueTasks : tâches en retard → Card
     *
     * CALCUL DE LA SEMAINE EN COURS :
     * ────────────────────────────────
     * startOfWeek = lundi de la semaine actuelle à minuit.
     * On utilise TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
     * pour obtenir le lundi (même si aujourd'hui EST un lundi).
     *
     * EXEMPLES :
     * - Mercredi 14 mai → lundi 12 mai à 00:00
     * - Lundi 12 mai → lundi 12 mai à 00:00 (previousOrSame = lui-même)
     * - Dimanche 18 mai → lundi 12 mai à 00:00
     *
     * @param authentication L'objet Spring Security contenant email + rôle
     * @return DashboardStatsResponse avec les 7 KPI
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("SERVICE : Dashboard stats — {} (rôle: {})", username, role);

        // ═══════════════════════════════════════════════════════
        // RBAC : Déterminer le contexte de filtrage
        // ═══════════════════════════════════════════════════════
        // ADMIN/MANAGER → null = pas de filtre utilisateur (vue globale)
        // USER → email = filtrer par tâches créées/assignées
        boolean isAdminOrManager = "ADMIN".equals(role) || "MANAGER".equals(role);
        String filterUsername = isAdminOrManager ? null : username;

        // 1. Compteur total de tâches actives
        long totalTasks = taskDashboardPort.countActiveTasks(filterUsername);

        // 2. Répartition par statut
        Map<String, Long> tasksByStatus = taskDashboardPort.countByStatus(filterUsername);

        // 3. Répartition par priorité
        Map<String, Long> tasksByPriority = taskDashboardPort.countByPriority(filterUsername);

        // 4. Tâches créées cette semaine
        // CALCUL : Lundi de la semaine en cours à minuit
        LocalDateTime startOfWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        long tasksCreatedThisWeek = taskDashboardPort.countCreatedAfter(filterUsername, startOfWeek);

        // 5. Tâches complétées cette semaine
        long tasksCompletedThisWeek = taskDashboardPort.countCompletedAfter(filterUsername, startOfWeek);

        // 6. Tâches en retard (dueDate < aujourd'hui ET status ≠ DONE)
        long overdueTasks = taskDashboardPort.countOverdueTasks(filterUsername);

        // 7. Activités récentes (10 dernières)
        List<ActivityLog> recentLogs = activityLogPort.findRecent(10);
        List<ActivityLogResponse> recentActivities = recentLogs.stream()
                .map(ActivityLogResponse::fromDomain)
                .toList();

        // Assemblage du DTO de réponse
        DashboardStatsResponse response = new DashboardStatsResponse(
                totalTasks, tasksByStatus, tasksByPriority,
                recentActivities, tasksCreatedThisWeek,
                tasksCompletedThisWeek, overdueTasks
        );

        log.info("SERVICE : Dashboard stats calculées — total={}, overdue={}, createdThisWeek={}",
                totalTasks, overdueTasks, tasksCreatedThisWeek);

        return response;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * MÉTHODE UTILITAIRE : extractRole
     * ═══════════════════════════════════════════════════════════════════
     *
     * Extrait le rôle depuis les authorities Spring Security.
     *
     * PRINCIPE SPRING SECURITY :
     * ──────────────────────────
     * Spring Security stocke les rôles comme des "GrantedAuthority"
     * avec le préfixe "ROLE_". Par exemple :
     * - Un ADMIN a l'authority "ROLE_ADMIN"
     * - Un USER a l'authority "ROLE_USER"
     * - Un MANAGER a l'authority "ROLE_MANAGER"
     *
     * Cette méthode retire le préfixe "ROLE_" pour obtenir le rôle brut.
     * Elle retourne "USER" par défaut si aucun rôle n'est trouvé.
     *
     * DÉPLACEMENT DEPUIS LE CONTRÔLEUR :
     * ──────────────────────────────────
     * AVANT : extractRole() était une méthode privée dans DashboardController.
     *         Dupliquée dans TaskController et CommentController.
     * APRÈS : extractRole() est dans DashboardService. Le contrôleur n'a
     *         plus besoin de connaître le rôle — il passe Authentication.
     *
     * NOTE : Les autres contrôleurs (TaskController, CommentController) ont
     * aussi leur propre extractRole(). Idéalement, il faudrait une classe
     * utilitaire partagée (ex: SecurityUtils.extractRole()), mais ce n'est
     * pas l'objet de cette tâche.
     *
     * @param authentication L'objet Spring Security
     * @return Le rôle sans préfixe (ADMIN, MANAGER, ou USER)
     */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PHASE 3 — FEATURE 4 : Méthodes analytiques (Burndown + Vélocité)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Injection du port analytique.
     */
    private final DashboardAnalyticsPort analyticsPort;

    /**
     * Génère les données du Burndown Chart.
     *
     * PRINCIPE — CALCUL DU BURNDOWN :
     * ────────────────────────────────
     * 1. Déterminer la période (ex: 4 dernières semaines)
     * 2. Compter le total de tâches au début de la période
     * 3. Pour chaque jour, compter les tâches restantes
     * 4. La ligne idéale est une droite du total à 0
     *
     * LIGNE IDÉALE :
     * Point de départ = totalTasks (jour 1)
     * Point d'arrivée = 0 (dernier jour)
     * Pente = -totalTasks / nombreDeJours
     * Valeur jour J = totalTasks * (1 - J/nombreDeJours)
     *
     * LIGNE RÉELLE :
     * Pour chaque jour J, on compte les tâches créées avant J
     * qui ne sont PAS encore terminées à J.
     */
    @Transactional(readOnly = true)
    public BurndownDataResponse getBurndownData(Authentication authentication, int weeks) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        boolean isAdminOrManager = "ADMIN".equals(role) || "MANAGER".equals(role);
        String filterUsername = isAdminOrManager ? null : username;

        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusWeeks(weeks);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        // Total de tâches au début de la période
        long totalTasks = analyticsPort.countCreatedBefore(filterUsername, startDateTime.plusDays(1));
        if (totalTasks == 0) totalTasks = taskDashboardPort.countActiveTasks(filterUsername);

        long totalDays = ChronoUnit.DAYS.between(startDate, now) + 1;

        // Ligne idéale : droite de totalTasks à 0
        List<BurndownDataResponse.DataPoint> idealLine = new ArrayList<>();
        List<BurndownDataResponse.DataPoint> actualLine = new ArrayList<>();

        for (long day = 0; day <= totalDays; day++) {
            LocalDate currentDate = startDate.plusDays(day);
            String dateStr = currentDate.toString();

            // Ligne idéale
            double idealValue = totalTasks * (1.0 - (double) day / totalDays);
            idealLine.add(new BurndownDataResponse.DataPoint(dateStr, Math.round(idealValue)));

            // Ligne réelle (tâches restantes à cette date)
            // On ne calcule que jusqu'à aujourd'hui (pas de données futures)
            if (!currentDate.isAfter(now)) {
                long remaining = analyticsPort.countRemainingTasks(filterUsername, currentDate.plusDays(1).atStartOfDay());
                actualLine.add(new BurndownDataResponse.DataPoint(dateStr, remaining));
            }
        }

        return new BurndownDataResponse(idealLine, actualLine, totalTasks,
                startDate.toString(), now.toString());
    }

    /**
     * Génère les données de vélocité.
     *
     * PRINCIPE — CALCUL DE LA VÉLOCITÉ :
     * ────────────────────────────────────
     * 1. Déterminer la période (ex: 8 dernières semaines)
     * 2. Pour chaque semaine, compter les tâches complétées
     * 3. Calculer la vélocité moyenne
     *
     * VÉLOCITÉ HEBDOMADAIRE :
     * Semaine 1 : 5 tâches complétées
     * Semaine 2 : 7 tâches complétées
     * Semaine 3 : 6 tâches complétées
     * Vélocité moyenne : (5+7+6)/3 = 6 tâches/semaine
     */
    @Transactional(readOnly = true)
    public VelocityDataResponse getVelocityData(Authentication authentication, int weeks) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        boolean isAdminOrManager = "ADMIN".equals(role) || "MANAGER".equals(role);
        String filterUsername = isAdminOrManager ? null : username;

        LocalDate now = LocalDate.now();
        List<VelocityDataResponse.WeeklyData> weeklyData = new ArrayList<>();
        long totalCompleted = 0;

        for (int w = weeks - 1; w >= 0; w--) {
            LocalDate weekStart = now.minusWeeks(w).with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusWeeks(1);

            long completed = analyticsPort.countCompletedBetween(
                    filterUsername,
                    weekStart.atStartOfDay(),
                    weekEnd.atStartOfDay()
            );

            String weekLabel = "S" + (weeks - w);
            weeklyData.add(new VelocityDataResponse.WeeklyData(weekLabel, completed));
            totalCompleted += completed;
        }

        double averageVelocity = weeks > 0 ? (double) totalCompleted / weeks : 0.0;

        return new VelocityDataResponse(weeklyData, averageVelocity, weeks);
    }
}