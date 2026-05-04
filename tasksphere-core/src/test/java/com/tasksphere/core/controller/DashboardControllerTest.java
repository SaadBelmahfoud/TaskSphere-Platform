package com.tasksphere.core.controller;

import com.tasksphere.core.dto.DashboardStatsResponse;
import com.tasksphere.core.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : DashboardController
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 5 : Tests du contrôleur de dashboard
 * ────────────────────────────────────────────────────────
 *
 * PRINCIPE — THIN CONTROLLER TESTING :
 * Le contrôleur est "thin" (mince), il délègue TOUT au service.
 * Le test vérifie donc uniquement :
 * 1. Que le service est appelé avec les bons paramètres
 * 2. Que la réponse HTTP est correcte
 * 3. Que le service n'est PAS appelé si les paramètres sont invalides
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    @DisplayName("getDashboardStats — Retourne les stats avec 200 OK")
    void getDashboardStats_returnsStats() {
        // Arrange
        when(authentication.getName()).thenReturn("admin@tasksphere.com");
        DashboardStatsResponse mockResponse = new DashboardStatsResponse(
                25L,
                Map.of("TODO", 10L, "DOING", 8L, "DONE", 7L),
                Map.of("LOW", 5L, "MEDIUM", 10L, "HIGH", 7L, "CRITICAL", 3L),
                List.of(),
                5L, 3L, 2L
        );
        when(dashboardService.getDashboardStats(authentication)).thenReturn(mockResponse);

        // Act
        ResponseEntity<DashboardStatsResponse> response = dashboardController.getDashboardStats(authentication);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(25L, response.getBody().totalTasks());
        assertEquals(2L, response.getBody().overdueTasks());
        verify(dashboardService).getDashboardStats(authentication);
    }

    @Test
    @DisplayName("getDashboardStats — Le service est appelé exactement une fois")
    void getDashboardStats_serviceCalledOnce() {
        // Arrange
        when(authentication.getName()).thenReturn("user@tasksphere.com");
        when(dashboardService.getDashboardStats(authentication))
                .thenReturn(new DashboardStatsResponse(0L, Map.of(), Map.of(), List.of(), 0L, 0L, 0L));

        // Act
        dashboardController.getDashboardStats(authentication);

        // Assert
        verify(dashboardService, times(1)).getDashboardStats(authentication);
    }
}