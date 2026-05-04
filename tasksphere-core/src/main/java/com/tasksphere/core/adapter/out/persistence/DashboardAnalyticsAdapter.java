package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.port.out.DashboardAnalyticsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : DashboardAnalyticsAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 4 : Implémentation du port analytique
 * ──────────────────────────────────────────────────
 *
 * Délègue les requêtes au TaskRepository existant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardAnalyticsAdapter implements DashboardAnalyticsPort {

    private final TaskRepository taskRepository;

    @Override
    public long countRemainingTasks(String username, LocalDateTime date) {
        return taskRepository.countRemainingTasksAt(username, date);
    }

    @Override
    public long countCompletedBetween(String username, LocalDateTime from, LocalDateTime to) {
        return taskRepository.countCompletedBetween(username, from, to);
    }

    @Override
    public long countCreatedBefore(String username, LocalDateTime before) {
        return taskRepository.countCreatedBefore(username, before);
    }
}