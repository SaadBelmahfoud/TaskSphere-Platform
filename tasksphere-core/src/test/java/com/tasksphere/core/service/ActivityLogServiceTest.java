package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.port.out.ActivityLogPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : ActivityLogService (NOUVEAU)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE DE L'AUDIT LOG :
 * ────────────────────────
 * L'ActivityLog est un ENREGISTREMENT EN AJOUT SEUL (append-only).
 * Il ne peut être ni modifié ni supprimé — uniquement créé.
 *
 * Le service ActivityLogService est un service transversal (cross-cutting)
 * qui encapsule la création de l'objet ActivityLog et sa sauvegarde.
 * Il est appelé par TaskManager, CommentManager, et le UserAdminEventListener.
 *
 * TESTS :
 * - Vérifier que log() crée bien un ActivityLog avec les bons paramètres
 * - Vérifier que l'ActivityLog est bien sauvegardé via le port
 * - Vérifier que l'ID est généré automatiquement (UUID)
 * - Vérifier que le timestamp est renseigné automatiquement
 */
@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogPort activityLogPort;

    @InjectMocks
    private ActivityLogService activityLogService;

    @Test
    @DisplayName("log() devrait créer et sauvegarder un ActivityLog avec les bons paramètres")
    void log_shouldCreateAndSaveActivityLog() {
        // ARRANGE
        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        when(activityLogPort.save(any(ActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        activityLogService.log(
                ActivityLog.Action.TASK_CREATED,
                "Tâche créée avec succès",
                "user@test.com",
                "task-123",
                "Ma tâche"
        );

        // ASSERT
        verify(activityLogPort, times(1)).save(captor.capture());
        ActivityLog captured = captor.getValue();

        assertThat(captured.action()).isEqualTo("TASK_CREATED");
        assertThat(captured.description()).isEqualTo("Tâche créée avec succès");
        assertThat(captured.username()).isEqualTo("user@test.com");
        assertThat(captured.taskId()).isEqualTo("task-123");
        assertThat(captured.taskTitle()).isEqualTo("Ma tâche");
        assertThat(captured.id()).isNotNull().isNotEmpty();
        assertThat(captured.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("log() avec taskId null devrait fonctionner (audit global)")
    void log_withNullTaskId_shouldWork() {
        when(activityLogPort.save(any(ActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        activityLogService.log(
                ActivityLog.Action.USER_ROLE_CHANGED,
                "Rôle changé",
                "admin@test.com",
                null,
                null
        );

        verify(activityLogPort, times(1)).save(any(ActivityLog.class));
    }

    @Test
    @DisplayName("Chaque action d'audit doit avoir un ID unique")
    void log_shouldGenerateUniqueIds() {
        when(activityLogPort.save(any(ActivityLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        activityLogService.log(ActivityLog.Action.TASK_CREATED, "T1", "u1", "t1", "Titre 1");
        activityLogService.log(ActivityLog.Action.TASK_UPDATED, "T2", "u1", "t1", "Titre 1");

        ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogPort, times(2)).save(captor.capture());

        String id1 = captor.getAllValues().get(0).id();
        String id2 = captor.getAllValues().get(1).id();

        assertThat(id1).isNotEqualTo(id2);
    }
}