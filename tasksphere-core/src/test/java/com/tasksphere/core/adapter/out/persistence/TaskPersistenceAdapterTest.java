package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : TaskPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 5 : Tests de l'adaptateur de persistance
 * ────────────────────────────────────────────────────────────
 *
 * PRINCIPE — TESTS DE L'ADAPTATEUR :
 * L'adaptateur est la passerelle entre le domaine et la BDD.
 * On mock le Repository JPA pour tester UNIQUEMENT la logique
 * de traduction Domain ↔ Entity.
 */
@ExtendWith(MockitoExtension.class)
class TaskPersistenceAdapterTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskPersistenceAdapter adapter;

    private Task testTask;
    private TaskEntity testEntity;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        testTask = new Task("task-1", "Test Task", "Description",
                Task.TaskStatus.TODO, Task.TaskPriority.MEDIUM,
                null, null, now, now, null, "user@test.com", null);

        testEntity = new TaskEntity(testTask);
    }

    @Test
    @DisplayName("save — Nouvelle tâche → INSERT")
    void save_newTask_inserts() {
        // Arrange
        when(taskRepository.findByIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.empty());
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(testEntity);

        // Act
        Task result = adapter.save(testTask);

        // Assert
        assertNotNull(result);
        assertEquals("Test Task", result.title());
        verify(taskRepository).save(any(TaskEntity.class));
    }

    @Test
    @DisplayName("save — Tâche existante → UPDATE via dirty checking")
    void save_existingTask_updates() {
        // Arrange
        TaskEntity existingEntity = new TaskEntity(testTask);
        existingEntity.markNotNew(); // Simuler une entité chargée depuis la DB
        when(taskRepository.findByIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(existingEntity));

        Task updatedTask = testTask.update("Updated Title", testTask.description());

        // Act
        Task result = adapter.save(updatedTask);

        // Assert
        assertNotNull(result);
        verify(taskRepository, never()).save(any()); // Dirty checking, pas de save explicite
    }

    @Test
    @DisplayName("findById — Tâche trouvée")
    void findById_found_returnsTask() {
        // Arrange
        when(taskRepository.findByIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(testEntity));

        // Act
        Optional<Task> result = adapter.findById("task-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Task", result.get().title());
    }

    @Test
    @DisplayName("findById — Tâche non trouvée")
    void findById_notFound_returnsEmpty() {
        // Arrange
        when(taskRepository.findByIdAndDeletedAtIsNull("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<Task> result = adapter.findById("nonexistent");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByUserIsOwnerOrAssignee — Retourne les tâches paginées")
    void findByUserIsOwnerOrAssignee_returnsPagedTasks() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<TaskEntity> entityPage = new PageImpl<>(List.of(testEntity));
        when(taskRepository.findByUserIsOwnerOrAssignee("user@test.com", "user@test.com", pageable))
                .thenReturn(entityPage);

        // Act
        Page<Task> result = adapter.findByUserIsOwnerOrAssignee("user@test.com", pageable);

        // Assert
        assertEquals(1, result.getContent().size());
        assertEquals("Test Task", result.getContent().get(0).title());
    }

    @Test
    @DisplayName("softDelete — Marque la tâche comme supprimée")
    void softDelete_marksTaskAsDeleted() {
        // Arrange
        when(taskRepository.findByIdAndDeletedAtIsNull("task-1")).thenReturn(Optional.of(testEntity));
        when(taskRepository.save(any(TaskEntity.class))).thenReturn(testEntity);

        // Act
        adapter.softDelete("task-1");

        // Assert
        verify(taskRepository).save(any(TaskEntity.class));
    }

    @Test
    @DisplayName("countAll — Retourne le nombre total de tâches actives")
    void countAll_returnsCount() {
        // Arrange
        when(taskRepository.countByDeletedAtIsNull()).thenReturn(42L);

        // Act
        long result = adapter.countAll();

        // Assert
        assertEquals(42L, result);
    }
}