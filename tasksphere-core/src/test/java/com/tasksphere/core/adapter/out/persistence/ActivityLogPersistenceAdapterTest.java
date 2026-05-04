package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.ActivityLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : ActivityLogPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 5 : Tests de l'adaptateur de persistance des logs
 * ──────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
class ActivityLogPersistenceAdapterTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private ActivityLogPersistenceAdapter adapter;

    private ActivityLog testLog;
    private ActivityLogEntity testEntity;

    @BeforeEach
    void setUp() {
        testLog = ActivityLog.create(
                ActivityLog.Action.TASK_CREATED,
                "Tâche créée",
                "user@test.com",
                "task-1",
                "Test Task"
        );

        testEntity = new ActivityLogEntity(testLog);
    }

    @Test
    @DisplayName("save — Enregistre un nouveau log d'activité")
    void save_newLog_savesSuccessfully() {
        // Arrange
        when(activityLogRepository.save(any(ActivityLogEntity.class))).thenReturn(testEntity);

        // Act
        ActivityLog result = adapter.save(testLog);

        // Assert
        assertNotNull(result);
        assertEquals(ActivityLog.Action.TASK_CREATED.name(), result.action());
        assertEquals("user@test.com", result.username());
        verify(activityLogRepository).save(any(ActivityLogEntity.class));
    }

    @Test
    @DisplayName("findRecent — Retourne les N logs les plus récents")
    void findRecent_returnsLimitedLogs() {
        // Arrange — utilise findRecent(int, Pageable) au lieu de findTop10ByOrderByTimestampDesc()
        when(activityLogRepository.findRecent(eq(10), any(Pageable.class)))
                .thenReturn(List.of(testEntity));

        // Act
        var result = adapter.findRecent(10);

        // Assert
        assertEquals(1, result.size());
        assertEquals("user@test.com", result.get(0).username());
    }

    @Test
    @DisplayName("findAll — Retourne les logs paginés")
    void findAll_returnsPagedLogs() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<ActivityLogEntity> entityPage = new PageImpl<>(List.of(testEntity));
        when(activityLogRepository.findAllWithFilter(isNull(), any(Pageable.class))).thenReturn(entityPage);

        // Act
        Page<ActivityLog> result = adapter.findAll(null, pageable);

        // Assert
        assertEquals(1, result.getContent().size());
    }
}