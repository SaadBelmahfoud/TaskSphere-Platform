package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.EventPublisherPort;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.port.out.UserInformationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ====================================================================
 * TESTS UNITAIRES : TaskManager (Service métier)
 * ====================================================================
 *
 * CONCEPT - Mockito (framework de mocking) :
 * ==========================================
 * Un "mock" est un FAUX objet qui simule le comportement d'une dépendance.
 * Ici, on mock les 3 ports (persistence, events, IAM) pour tester TaskManager
 * ISOLÉMENT, sans base de données ni serveur HTTP.
 *
 * CONCEPT - @Mock :
 * Crée un faux objet. Par défaut, toutes ses méthodes retournent null/0/false.
 * On doit "configurer" le comportement avec when().thenReturn().
 *
 * CONCEPT - @InjectMocks :
 * Crée l'objet à tester (TaskManager) et injecte automatiquement les @Mock
 * dans ses dépendances (constructeur).
 *
 * CONCEPT - verify() :
 * Permet de vérifier qu'une méthode du mock a été appelée.
 * Ex: verify(persistencePort).save(any()) → "le save a bien été appelé ?"
 *
 * CONCEPT - ArgumentCaptor :
 * Capture les arguments passés à un mock pour vérifier leur contenu.
 */
@ExtendWith(MockitoExtension.class)  // Active Mockito pour ce test
class TaskManagerTest {

    @Mock   // Faux port de persistance (simule la BDD)
    private TaskPersistencePort persistencePort;

    @Mock   // Faux éditeur d'événements (simule Kafka/domain events)
    private EventPublisherPort eventPublisher;

    @Mock   // Faux adaptateur IAM (simule le module de gestion des utilisateurs)
    private UserInformationPort userInformationPort;

    @InjectMocks  // Crée TaskManager avec les 3 mocks injectés
    private TaskManager taskManager;

    // ============================================================
    // TESTS : CRÉATION
    // ============================================================

    @Test
    @DisplayName("createTask() devrait sauvegarder la tâche et publier un événement")
    void createTask_shouldSaveAndPublishEvent() {
        // ARRANGE : préparer le comportement des mocks
        when(userInformationPort.getUserInfo("saadoune"))
                .thenReturn(new UserInfo("saadoune", "ROLE_USER"));
        when(persistencePort.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // retourne l'objet passé

        // ACT : appeler la méthode à tester
        Task result = taskManager.createTask("Ma tâche", "Description", "saadoune");

        // ASSERT : vérifier les résultats
        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Ma tâche");
        assertThat(result.description()).isEqualTo("Description");
        assertThat(result.status()).isEqualTo(Task.TaskStatus.TODO);
        assertThat(result.priority()).isEqualTo(Task.TaskPriority.MEDIUM);
        assertThat(result.userId()).isEqualTo("saadoune");

        // Vérifier que persistencePort.save() a été appelé exactement 1 fois
        verify(persistencePort, times(1)).save(any(Task.class));

        // Vérifier que l'événement a été publié
        verify(eventPublisher, times(1)).publishTaskCreated(any());
    }

    @Test
    @DisplayName("createTask() avec description null devrait utiliser une chaîne vide")
    void createTask_withNullDescription_shouldUseEmptyString() {
        when(userInformationPort.getUserInfo("saadoune"))
                .thenReturn(new UserInfo("saadoune", "ROLE_USER"));
        when(persistencePort.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskManager.createTask("Titre", null, "saadoune");

        assertThat(result.description()).isEqualTo("");
    }

    // ============================================================
    // TESTS : RÉCUPÉRATION
    // ============================================================

    @Test
    @DisplayName("getTaskById() devrait retourner la tâche si elle appartient à l'utilisateur")
    void getTaskById_shouldReturnTaskIfOwner() {
        Task task = Task.create("Ma tâche", "Desc", "saadoune");
        when(persistencePort.findByIdAndUserId("task-1", "saadoune"))
                .thenReturn(Optional.of(task));

        Optional<Task> result = taskManager.getTaskById("task-1", "saadoune");

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Ma tâche");
    }

    @Test
    @DisplayName("getTaskById() devrait retourner empty si la tâche appartient à un autre")
    void getTaskById_shouldReturnEmptyIfNotOwner() {
        when(persistencePort.findByIdAndUserId("task-1", "autre-user"))
                .thenReturn(Optional.empty());

        Optional<Task> result = taskManager.getTaskById("task-1", "autre-user");

        assertThat(result).isEmpty();
    }

    // ============================================================
    // TESTS : MISE À JOUR
    // ============================================================

    @Test
    @DisplayName("updateTask() devrait mettre à jour uniquement les champs fournis")
    void updateTask_shouldUpdateOnlyProvidedFields() {
        Task existing = Task.create("Original", "Desc originale", "saadoune");
        when(persistencePort.findByIdAndUserId("task-1", "saadoune"))
                .thenReturn(Optional.of(existing));
        when(persistencePort.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Ne modifier que le titre (description et priorité null = pas modifiés)
        Optional<Task> result = taskManager.updateTask(
                "task-1", "saadoune", "Nouveau titre", null, null, null);

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Nouveau titre");
        assertThat(result.get().description()).isEqualTo("Desc originale");
        assertThat(result.get().priority()).isEqualTo(Task.TaskPriority.MEDIUM); // inchangé
    }

    @Test
    @DisplayName("updateTask() sur tâche inexistante devrait retourner empty")
    void updateTask_nonExistent_shouldReturnEmpty() {
        when(persistencePort.findByIdAndUserId("task-999", "saadoune"))
                .thenReturn(Optional.empty());

        Optional<Task> result = taskManager.updateTask(
                "task-999", "saadoune", "Titre", null, null, null);

        assertThat(result).isEmpty();
        // Le save NE DOIT PAS être appelé si la tâche n'existe pas
        verify(persistencePort, never()).save(any());
    }

    // ============================================================
    // TESTS : CHANGEMENT DE STATUT
    // ============================================================

    @Test
    @DisplayName("updateTaskStatus(DONE) devrait renseigner completedAt")
    void updateTaskStatus_done_shouldSetCompletedAt() {
        Task existing = Task.create("Tâche", "Desc", "saadoune");
        when(persistencePort.findByIdAndUserId("task-1", "saadoune"))
                .thenReturn(Optional.of(existing));
        when(persistencePort.save(any(Task.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Task> result = taskManager.updateTaskStatus("task-1", "saadoune", "DONE");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(Task.TaskStatus.DONE);
        assertThat(result.get().completedAt()).isNotNull();
    }

    // ============================================================
    // TESTS : SOFT DELETE
    // ============================================================

    @Test
    @DisplayName("deleteTask() devrait appeler softDelete et retourner true")
    void deleteTask_shouldCallSoftDeleteAndReturnTrue() {
        Task existing = Task.create("Tâche", "Desc", "saadoune");
        when(persistencePort.findByIdAndUserId("task-1", "saadoune"))
                .thenReturn(Optional.of(existing));

        boolean result = taskManager.deleteTask("task-1", "saadoune");

        assertThat(result).isTrue();
        // Vérifier que softDelete a été appelé avec le bon ID
        verify(persistencePort, times(1)).softDelete("task-1");
    }

    @Test
    @DisplayName("deleteTask() sur tâche inexistante devrait retourner false sans appeler softDelete")
    void deleteTask_nonExistent_shouldReturnFalse() {
        when(persistencePort.findByIdAndUserId("task-999", "saadoune"))
                .thenReturn(Optional.empty());

        boolean result = taskManager.deleteTask("task-999", "saadoune");

        assertThat(result).isFalse();
        verify(persistencePort, never()).softDelete(any());
    }
}