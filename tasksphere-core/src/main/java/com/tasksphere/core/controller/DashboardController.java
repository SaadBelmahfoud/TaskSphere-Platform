package com.tasksphere.core.controller;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.dto.ActivityLogResponse;
import com.tasksphere.core.dto.DashboardStatsResponse;
import com.tasksphere.core.port.out.ActivityLogPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : DashboardController (API REST Dashboard)
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU — CONCEPT DASHBOARD :
 * ──────────────────────────────
 * Un dashboard agrège des données de PLUSIEURS sources pour donner
 * une vue d'ensemble au utilisateur. C'est un pattern classique
 * dans les applications de gestion (project management, CRM, etc.)
 *
 * ENDPOINT :
 * ──────────
 * GET /api/v1/dashboard/stats → DashboardStatsResponse
 *
 * DONNÉES AGRÉGÉES :
 * ──────────────────
 * 1. totalTasks : compteur global de tâches actives
 * 2. tasksByStatus : Map {TODO: N, DOING: N, DONE: N} → PieChart
 * 3. tasksByPriority : Map {LOW: N, MEDIUM: N, HIGH: N, CRITICAL: N} → BarChart
 * 4. recentActivities : 10 dernières actions → Timeline
 * 5. tasksCreatedThisWeek : compteur de la semaine → Card
 * 6. tasksCompletedThisWeek : compteur de la semaine → Card
 * 7. overdueTasks : tâches en retard → Card
 *
 * RBAC :
 * ──────
 * ADMIN/MANAGER → voient les stats GLOBALES (toutes les tâches)
 * USER → voient les stats de leurs tâches + tâches assignées
 * Le filtre est appliqué via les méthodes du TaskPersistencePort.
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

    private final TaskPersistencePort taskPersistencePort;
    private final ActivityLogPort activityLogPort;

    /**
     * GET /api/v1/dashboard/stats
     *
     * Agrège les statistiques pour le Dashboard.
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            Authentication authentication) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : GET /dashboard/stats — {} (rôle: {})", username, role);

        boolean isAdminOrManager = "ADMIN".equals(role) || "MANAGER".equals(role);

        // 1. Compteur total de tâches
        long totalTasks = taskPersistencePort.countActiveTasks(isAdminOrManager ? null : username);

        // 2. Répartition par statut
        Map<String, Long> tasksByStatus = taskPersistencePort.countByStatus(isAdminOrManager ? null : username);

        // 3. Répartition par priorité
        Map<String, Long> tasksByPriority = taskPersistencePort.countByPriority(isAdminOrManager ? null : username);

        // 4. Tâches créées cette semaine
        LocalDateTime startOfWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        long tasksCreatedThisWeek = taskPersistencePort.countCreatedAfter(
                isAdminOrManager ? null : username, startOfWeek);

        // 5. Tâches complétées cette semaine
        long tasksCompletedThisWeek = taskPersistencePort.countCompletedAfter(
                isAdminOrManager ? null : username, startOfWeek);

        // 6. Tâches en retard (dueDate < aujourd'hui ET status ≠ DONE)
        long overdueTasks = taskPersistencePort.countOverdueTasks(isAdminOrManager ? null : username);

        // 7. Activités récentes
        List<ActivityLog> recentLogs = activityLogPort.findRecent(10);
        List<ActivityLogResponse> recentActivities = recentLogs.stream()
                .map(ActivityLogResponse::fromDomain)
                .toList();

        DashboardStatsResponse response = new DashboardStatsResponse(
                totalTasks, tasksByStatus, tasksByPriority,
                recentActivities, tasksCreatedThisWeek,
                tasksCompletedThisWeek, overdueTasks
        );

        return ResponseEntity.ok(response);
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }
}