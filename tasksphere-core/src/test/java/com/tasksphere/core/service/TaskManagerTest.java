package com.tasksphere.core.service;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.port.out.UserInformationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : TaskManager (Service métier — CORRIGÉ & ENRICHI)
 * ═══════════════════════════════════════════════════════════════════
 *
 * CORRECTIONS APPORTÉES (v2) :
 * ─────────────────────────────
 * 1. Suppression des stubbings inutiles de userInformationPort.getUserInfo()
 *    dans les tests où assigneeId est null (UnnecessaryStubbingException)
 *
 *    EXPLICATION THÉORIQUE :
 *    ───────────────────────
 *    Mockito en mode STRICT (par défaut avec MockitoExtension) détecte
 *    les stubbings qui ne sont jamais "consommés" (la méthode stubbée
 *    n'est jamais appelée). C'est un code smell : cela indique soit :
 *    - Un test mal écrit (on stubbe une dépendance non utilisée)
 *    - Un changement dans le code de production qui rend le stubbing obsolète
 *    - Une mécompréhension du flux d'exécution
 *
 *    Dans notre cas, c'était la 3e option : le test stubbait
 *    userInformationPort.getUserInfo() parce qu'il pensait que
 *    createTask() l'appelait TOUJOURS. Mais en réalité, cette méthode
 *    n'est appelée QUE pour vérifier le RBAC lors d'une assignation.
 *
 *    RÈGLE PRATIQUE :
 *    ─────────────────
 *    Ne stubber QUE les méthodes qui seront effectivement appelées
 *    par le code testé. Si un stubbing n'est pas consommé, c'est
 *    probablement une erreur de compréhension du flux.
 *
 * 2. Toutes les signatures de méthode mises à jour pour correspondre
 *    au code actuel (3+ paramètres avec rôle RBAC)
 * 3. Ajout du mock ActivityLogService (était manquant)
 * 4. Tests pour assignTask() — RBAC MANAGER/ADMIN/USER
 * 5. Tests pour searchTasks() — RBAC ADMIN vs USER
 * 6. Tests pour getTaskById() — propriétaire vs assigné vs ADMIN
 * 7. Tests pour updateTask() — avec rôle
 * 8. Tests pour deleteTask() — ADMIN vs propriétaire vs non-propriétaire
 *
 * PRINCIPE DE MOCKITO :
 * ─────────────────────
 * @Mock crée un FAUX objet → on configure son comportement avec when().thenReturn()
 * @InjectMocks crée l'objet testé et injecte les mocks dans ses dépendances
 * verify() vérifie qu'une méthode du mock a été appelée
 * ArgumentCaptor capture les arguments passés au mock pour vérification
 *
 * PRINCIPE @Nested :
 * ──────────────────
 * Groupement logique des tests par méthode testée.
 * Avantages :
 * - Lecture plus claire dans les rapports
 * - Setup commun par groupe
 * - Isolation conceptuelle
 */
@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    @Mock
    private TaskPersistencePort persistencePort;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private UserInformationPort userInformationPort;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private TaskManager taskManager;

    // ═══════════════════════════════════════════════════════
    // CRÉATION DE TÂCHE
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("createTask()")
    class CreateTask {

        @Test
        @DisplayName("Devrait sauvegarder la tâche et publier un événement")
        void shouldSaveAndPublishEvent() {
            // ARRANGE : préparer le comportement des mocks
            // NOTE : userInformationPort.getUserInfo() N'est PAS stubbé car
            // assigneeId est null dans l'appel 3 params → getUserInfo jamais appelé
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // ACT : appeler la méthode à tester (version 3 params → assigneeId = null)
            Task result = taskManager.createTask("Ma tâche", "Description", "saadoune@tasksphere.com");

            // ASSERT : vérifier les résultats
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Ma tâche");
            assertThat(result.status()).isEqualTo(Task.TaskStatus.TODO);
            assertThat(result.priority()).isEqualTo(Task.TaskPriority.MEDIUM);
            assertThat(result.userId()).isEqualTo("saadoune@tasksphere.com");
            assertThat(result.assigneeId()).isNull();

            // Vérifier que persistencePort.save() a été appelé exactement 1 fois
            verify(persistencePort, times(1)).save(any(Task.class));
            // Vérifier que l'événement a été publié
            verify(eventPublisher, times(1)).publishTaskCreated(any());
            // Vérifier que l'audit a été enregistré
            verify(activityLogService, times(1)).log(
                    eq(ActivityLog.Action.TASK_CREATED), anyString(),
                    eq("saadoune@tasksphere.com"), anyString(), anyString());
            // Vérifier que getUserInfo n'a PAS été appelé (pas d'assignation)
            verify(userInformationPort, never()).getUserInfo(anyString());
        }

        @Test
        @DisplayName("Avec description null devrait utiliser une chaîne vide")
        void withNullDescription_shouldUseEmptyString() {
            // NOTE : pas de stubbing de getUserInfo car assigneeId = null
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Titre", null, "saadoune@tasksphere.com");

            assertThat(result.description()).isEqualTo("");
        }

        @Test
        @DisplayName("Version 6 params avec priorité HIGH et dueDate")
        void withFullParams_shouldSetAllFields() {
            // ICI on stub getUserInfo car assigneeId est non-null → RBAC vérifié
            when(userInformationPort.getUserInfo("manager@tasksphere.com"))
                    .thenReturn(new UserInfo("manager", "ROLE_MANAGER"));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LocalDate dueDate = LocalDate.of(2026, 6, 15);
            Task result = taskManager.createTask(
                    "Tâche complète", "Description", "manager@tasksphere.com",
                    "HIGH", dueDate, "saadoune@tasksphere.com"
            );

            assertThat(result.priority()).isEqualTo(Task.TaskPriority.HIGH);
            assertThat(result.dueDate()).isEqualTo(dueDate);
            assertThat(result.assigneeId()).isEqualTo("saadoune@tasksphere.com");
        }

        @Test
        @DisplayName("Priorité 'high' minuscule est normalisée en HIGH par toUpperCase()")
        void withLowercasePriority_shouldBeNormalizedToUppercase() {
            // CORRECTION B1 : "high".toUpperCase() = "HIGH" → valeur valide → acceptée
            // NOTE : pas de stubbing de getUserInfo car assigneeId = null
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Test", "Desc", "user@test.com",
                    "high", null, null);

            // "high".toUpperCase() = "HIGH" → valeur valide → acceptée
            assertThat(result.priority()).isEqualTo(Task.TaskPriority.HIGH);
        }

        @Test
        @DisplayName("Priorité totalement invalide ('URGENT') devrait tomber à MEDIUM")
        void withNonExistentPriority_shouldFallbackToMedium() {
            // NOTE : pas de stubbing de getUserInfo car assigneeId = null
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Test", "Desc", "user@test.com",
                    "URGENT", null, null);

            // "URGENT".toUpperCase() = "URGENT" → pas dans l'enum → MEDIUM par défaut
            assertThat(result.priority()).isEqualTo(Task.TaskPriority.MEDIUM);
        }

        @Test
        @DisplayName("USER qui tente d'assigner → assignation ignorée (B9)")
        void userTryingToAssign_shouldIgnoreAssignment() {
            // CORRECTION B9 : Défense en profondeur
            // ICI on stub getUserInfo car assigneeId est non-null → RBAC vérifié
            when(userInformationPort.getUserInfo("user@test.com"))
                    .thenReturn(new UserInfo("user", "ROLE_USER"));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Test", "Desc", "user@test.com",
                    null, null, "assignee@test.com");

            // L'assignation doit être ignorée car l'utilisateur est USER
            assertThat(result.assigneeId()).isNull();
        }

        @Test
        @DisplayName("MANAGER qui assigne → assignation acceptée")
        void managerAssigning_shouldSucceed() {
            when(userInformationPort.getUserInfo("manager@test.com"))
                    .thenReturn(new UserInfo("manager", "ROLE_MANAGER"));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Test", "Desc", "manager@test.com",
                    null, null, "assignee@test.com");

            assertThat(result.assigneeId()).isEqualTo("assignee@test.com");
        }

        @Test
        @DisplayName("ADMIN qui assigne → assignation acceptée")
        void adminAssigning_shouldSucceed() {
            when(userInformationPort.getUserInfo("admin@test.com"))
                    .thenReturn(new UserInfo("admin", "ROLE_ADMIN"));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Task result = taskManager.createTask("Test", "Desc", "admin@test.com",
                    null, null, "assignee@test.com");

            assertThat(result.assigneeId()).isEqualTo("assignee@test.com");
        }
    }

    // ═══════════════════════════════════════════════════════
    // RÉCUPÉRATION PAR ID AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTaskById()")
    class GetTaskById {

        @Test
        @DisplayName("USER propriétaire → peut voir sa tâche")
        void ownerUser_shouldSeeTask() {
            Task task = Task.create("Ma tâche", "Desc", "user@test.com");
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-1", "user@test.com"))
                    .thenReturn(Optional.of(task));

            Optional<Task> result = taskManager.getTaskById("task-1", "user@test.com", "USER");

            assertThat(result).isPresent();
            assertThat(result.get().title()).isEqualTo("Ma tâche");
        }

        @Test
        @DisplayName("USER assigné → peut voir la tâche (BUG 1 CORRIGÉ)")
        void assigneeUser_shouldSeeTask() {
            Task task = Task.createWithAssignee("Tâche assignée", "Desc", "owner@test.com", "assignee@test.com");
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-1", "assignee@test.com"))
                    .thenReturn(Optional.of(task));

            Optional<Task> result = taskManager.getTaskById("task-1", "assignee@test.com", "USER");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("USER non propriétaire ni assigné → ne voit pas la tâche")
        void otherUser_shouldNotSeeTask() {
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-1", "other@test.com"))
                    .thenReturn(Optional.empty());

            Optional<Task> result = taskManager.getTaskById("task-1", "other@test.com", "USER");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ADMIN → peut voir n'importe quelle tâche")
        void admin_shouldSeeAnyTask() {
            Task task = Task.create("Tâche privée", "Desc", "other@test.com");
            when(persistencePort.findById("task-1"))
                    .thenReturn(Optional.of(task));

            Optional<Task> result = taskManager.getTaskById("task-1", "admin@test.com", "ADMIN");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("MANAGER → peut voir n'importe quelle tâche")
        void manager_shouldSeeAnyTask() {
            Task task = Task.create("Tâche privée", "Desc", "other@test.com");
            when(persistencePort.findById("task-1"))
                    .thenReturn(Optional.of(task));

            Optional<Task> result = taskManager.getTaskById("task-1", "manager@test.com", "MANAGER");

            assertThat(result).isPresent();
        }
    }

    // ═══════════════════════════════════════════════════════
    // MISE À JOUR AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateTask()")
    class UpdateTask {

        @Test
        @DisplayName("USER propriétaire → peut modifier")
        void ownerUser_shouldUpdate() {
            Task existing = Task.create("Original", "Desc originale", "user@test.com");
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-1", "user@test.com"))
                    .thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTask(
                    "task-1", "user@test.com", "USER",
                    "Nouveau titre", null, null, null);

            assertThat(result).isPresent();
            assertThat(result.get().title()).isEqualTo("Nouveau titre");
            assertThat(result.get().description()).isEqualTo("Desc originale"); // inchangée
        }

        @Test
        @DisplayName("USER assigné → peut modifier (BUG 1 CORRIGÉ)")
        void assigneeUser_shouldUpdate() {
            Task existing = Task.createWithAssignee("Tâche", "Desc", "owner@test.com", "assignee@test.com");
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-1", "assignee@test.com"))
                    .thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTask(
                    "task-1", "assignee@test.com", "USER",
                    "Titre modifié", null, null, null);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Tâche inexistante → retourne empty")
        void nonExistent_shouldReturnEmpty() {
            when(persistencePort.findByIdAndUserIsOwnerOrAssignee("task-999", "user@test.com"))
                    .thenReturn(Optional.empty());

            Optional<Task> result = taskManager.updateTask(
                    "task-999", "user@test.com", "USER", "Titre", null, null, null);

            assertThat(result).isEmpty();
            verify(persistencePort, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN → peut modifier n'importe quelle tâche")
        void admin_shouldUpdateAnyTask() {
            Task existing = Task.create("Original", "Desc", "other@test.com");
            when(persistencePort.findById("task-1"))
                    .thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTask(
                    "task-1", "admin@test.com", "ADMIN",
                    "Titre admin", null, "HIGH", null);

            assertThat(result).isPresent();
            assertThat(result.get().priority()).isEqualTo(Task.TaskPriority.HIGH);
        }
    }

    // ═══════════════════════════════════════════════════════
    // CHANGEMENT DE STATUT
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateTaskStatus()")
    class UpdateTaskStatus {

        @Test
        @DisplayName("DONE → completedAt renseigné automatiquement")
        void done_shouldSetCompletedAt() {
            Task existing = Task.create("Tâche", "Desc", "user@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTaskStatus(
                    "task-1", "user@test.com", "USER", "DONE");

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(Task.TaskStatus.DONE);
            assertThat(result.get().completedAt()).isNotNull();
        }

        @Test
        @DisplayName("DOING → completedAt null")
        void doing_shouldNotSetCompletedAt() {
            Task existing = Task.create("Tâche", "Desc", "user@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTaskStatus(
                    "task-1", "user@test.com", "USER", "DOING");

            assertThat(result).isPresent();
            assertThat(result.get().completedAt()).isNull();
        }

        @Test
        @DisplayName("Non-propriétaire non-assigné → rejeté")
        void notOwnerNotAssignee_shouldBeRejected() {
            Task existing = Task.create("Tâche", "Desc", "other@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));

            Optional<Task> result = taskManager.updateTaskStatus(
                    "task-1", "stranger@test.com", "USER", "DONE");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Assigné → peut changer le statut")
        void assignee_shouldChangeStatus() {
            Task existing = Task.createWithAssignee("Tâche", "Desc", "owner@test.com", "assignee@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.updateTaskStatus(
                    "task-1", "assignee@test.com", "USER", "DOING");

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(Task.TaskStatus.DOING);
        }
    }

    // ═══════════════════════════════════════════════════════
    // ASSIGNATION DE TÂCHE
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("assignTask()")
    class AssignTask {

        @Test
        @DisplayName("MANAGER → peut assigner")
        void manager_shouldAssign() {
            Task existing = Task.create("Tâche", "Desc", "user@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.assignTask(
                    "task-1", "manager@test.com", "MANAGER", "assignee@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().assigneeId()).isEqualTo("assignee@test.com");
        }

        @Test
        @DisplayName("ADMIN → peut assigner")
        void admin_shouldAssign() {
            Task existing = Task.create("Tâche", "Desc", "user@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.assignTask(
                    "task-1", "admin@test.com", "ADMIN", "assignee@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().assigneeId()).isEqualTo("assignee@test.com");
        }

        @Test
        @DisplayName("USER → ne peut PAS assigner")
        void user_shouldNotAssign() {
            Optional<Task> result = taskManager.assignTask(
                    "task-1", "user@test.com", "USER", "assignee@test.com");

            assertThat(result).isEmpty();
            verify(persistencePort, never()).save(any());
        }

        @Test
        @DisplayName("Assigner avec assigneeId vide → désassignation")
        void emptyAssigneeId_shouldUnassign() {
            Task existing = Task.createWithAssignee("Tâche", "Desc", "owner@test.com", "old@test.com");
            when(persistencePort.findById("task-1")).thenReturn(Optional.of(existing));
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Task> result = taskManager.assignTask(
                    "task-1", "manager@test.com", "MANAGER", "");

            assertThat(result).isPresent();
            assertThat(result.get().assigneeId()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SOFT DELETE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteTask()")
    class DeleteTask {

        @Test
        @DisplayName("Propriétaire → peut supprimer")
        void owner_shouldDelete() {
            Task existing = Task.create("Tâche", "Desc", "user@test.com");
            when(persistencePort.findByIdAndUserId("task-1", "user@test.com"))
                    .thenReturn(Optional.of(existing));

            boolean result = taskManager.deleteTask("task-1", "user@test.com", "USER");

            assertThat(result).isTrue();
            verify(persistencePort, times(1)).softDelete("task-1");
        }

        @Test
        @DisplayName("ADMIN → peut supprimer n'importe quelle tâche")
        void admin_shouldDeleteAnyTask() {
            when(persistencePort.findById("task-1"))
                    .thenReturn(Optional.of(Task.create("Tâche", "Desc", "other@test.com")));

            boolean result = taskManager.deleteTask("task-1", "admin@test.com", "ADMIN");

            assertThat(result).isTrue();
            verify(persistencePort, times(1)).softDelete("task-1");
        }

        @Test
        @DisplayName("Non-propriétaire non-ADMIN → ne peut pas supprimer")
        void nonOwner_shouldNotDelete() {
            when(persistencePort.findByIdAndUserId("task-1", "other@test.com"))
                    .thenReturn(Optional.empty());

            boolean result = taskManager.deleteTask("task-1", "other@test.com", "USER");

            assertThat(result).isFalse();
            verify(persistencePort, never()).softDelete(any());
        }
    }

    // ═══════════════════════════════════════════════════════
    // RECHERCHE AVEC RBAC
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("searchTasks()")
    class SearchTasks {

        @Test
        @DisplayName("ADMIN → recherche globale (pas de filtre utilisateur)")
        void admin_shouldSearchGlobally() {
            Task task = Task.create("Tâche", "Desc", "other@test.com");
            Page<Task> page = new PageImpl<>(List.of(task));
            when(persistencePort.searchTasks(any(), any(Pageable.class)))
                    .thenReturn(page);

            var criteria = new TaskPersistencePort.TaskSearchCriteria(
                    null, null, null, null, null, null, null, null, null);

            Page<Task> result = taskManager.searchTasks(
                    criteria, 0, 20, "createdAt", "desc", "admin@test.com", "ADMIN");

            assertThat(result.getContent()).hasSize(1);
            verify(persistencePort).searchTasks(any(), any(Pageable.class));
            verify(persistencePort, never()).searchTasksForUser(anyString(), any(), any());
        }

        @Test
        @DisplayName("USER → recherche limitée aux tâches où il est impliqué")
        void user_shouldSearchOnlyOwnAndAssigned() {
            Task task = Task.create("Tâche", "Desc", "user@test.com");
            Page<Task> page = new PageImpl<>(List.of(task));
            when(persistencePort.searchTasksForUser(eq("user@test.com"), any(), any(Pageable.class)))
                    .thenReturn(page);

            var criteria = new TaskPersistencePort.TaskSearchCriteria(
                    null, null, null, null, null, null, null, null, null);

            Page<Task> result = taskManager.searchTasks(
                    criteria, 0, 20, "createdAt", "desc", "user@test.com", "USER");

            assertThat(result.getContent()).hasSize(1);
            verify(persistencePort).searchTasksForUser(eq("user@test.com"), any(), any(Pageable.class));
            verify(persistencePort, never()).searchTasks(any(), any(Pageable.class));
        }
    }

    // ═══════════════════════════════════════════════════════
    // AUDIT TRAIL — Fail-safe
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Audit Trail")
    class AuditTrail {

        @Test
        @DisplayName("Si l'audit échoue, l'opération métier réussit quand même")
        void auditFailure_shouldNotBlockBusinessOperation() {
            // ARRANGE : l'audit lève une exception
            // NOTE : pas de stubbing de getUserInfo car assigneeId = null (3 params)
            when(persistencePort.save(any(Task.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new RuntimeException("DB audit indisponible"))
                    .when(activityLogService).log(any(), anyString(), anyString(), anyString(), anyString());

            // ACT : la création ne doit PAS planter
            Task result = taskManager.createTask("Test audit fail", "Desc", "user@test.com");

            // ASSERT : la tâche est créée malgré l'échec de l'audit
            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Test audit fail");
        }
    }
}