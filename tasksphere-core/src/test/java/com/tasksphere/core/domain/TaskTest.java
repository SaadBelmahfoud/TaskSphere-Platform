package com.tasksphere.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * ====================================================================
 * TESTS UNITAIRES : Task (Objet de valeur du domaine)
 * ====================================================================
 *
 * CONCEPT - Pourquoi tester un record ?
 * Le record Task est le CŒUR du domaine. Toute la logique métier
 * (création, mise à jour, soft delete) y est codée.
 * Si le domaine est faux, toute l'application est fausse.
 *
 * Ces tests vérifient que les méthodes de Task retournent bien de
 * NOUVELLES instances (immutabilité) avec les bonnes valeurs.
 */
class TaskTest {

    // ============================================================
    // TESTS : CRÉATION (Factory Method)
    // ============================================================

    @Test
    @DisplayName("create() devrait initialiser avec les valeurs par défaut")
    void create_shouldSetDefaultValues() {
        Task task = Task.create("Ma tâche", "Description", "user-123");

        // Vérifier les valeurs par défaut
        assertThat(task.title()).isEqualTo("Ma tâche");
        assertThat(task.description()).isEqualTo("Description");
        assertThat(task.status()).isEqualTo(Task.TaskStatus.TODO);
        assertThat(task.priority()).isEqualTo(Task.TaskPriority.MEDIUM);
        assertThat(task.userId()).isEqualTo("user-123");
        assertThat(task.id()).isNotNull().isNotEmpty();
        assertThat(task.dueDate()).isNull();
        assertThat(task.completedAt()).isNull();
        assertThat(task.deletedAt()).isNull();
    }

    @Test
    @DisplayName("create() devrait générer un UUID comme ID")
    void create_shouldGenerateUuidId() {
        Task task = Task.create("Titre", "Desc", "user-1");

        // Un UUID format : 8-4-4-4-12 caractères hexadécimaux
        assertThat(task.id()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("create() avec description null devrait la remplacer par chaîne vide")
    void create_shouldHandleNullDescription() {
        Task task = Task.create("Titre", null, "user-1");

        assertThat(task.description()).isEqualTo("");
    }

    // ============================================================
    // TESTS : IMMUTABILITÉ
    // ============================================================

    @Test
    @DisplayName("update() devrait retourner une NOUVELLE instance (immutabilité)")
    void update_shouldReturnNewInstance() {
        Task original = Task.create("Original", "Desc", "user-1");

        Task updated = original.update("Modifié", "Nouvelle desc");

        // L'instance originale ne doit PAS être modifiée
        assertThat(original.title()).isEqualTo("Original");
        assertThat(original.description()).isEqualTo("Desc");

        // La nouvelle instance DOIT avoir les nouvelles valeurs
        assertThat(updated.title()).isEqualTo("Modifié");
        assertThat(updated.description()).isEqualTo("Nouvelle desc");
        // L'ID est conservé (même tâche)
        assertThat(updated.id()).isEqualTo(original.id());
    }

    // ============================================================
    // TESTS : CHANGEMENT DE STATUT
    // ============================================================

    @Test
    @DisplayName("updateStatus(DOING) ne doit PAS renseigner completedAt")
    void updateStatus_doing_shouldNotSetCompletedAt() {
        Task task = Task.create("Tâche", "Desc", "user-1");

        Task doing = task.updateStatus(Task.TaskStatus.DOING);

        assertThat(doing.status()).isEqualTo(Task.TaskStatus.DOING);
        assertThat(doing.completedAt()).isNull();
    }

    @Test
    @DisplayName("updateStatus(DONE) DOIT renseigner completedAt automatiquement")
    void updateStatus_done_shouldSetCompletedAt() {
        Task task = Task.create("Tâche", "Desc", "user-1");

        Task done = task.updateStatus(Task.TaskStatus.DONE);

        assertThat(done.status()).isEqualTo(Task.TaskStatus.DONE);
        assertThat(done.completedAt()).isNotNull();
        // completedAt doit être dans les dernières secondes
        assertThat(done.completedAt()).isBeforeOrEqualTo(java.time.LocalDateTime.now());
    }

    @Test
    @DisplayName("updateStatus(TODO) depuis DONE doit remettre completedAt à null")
    void updateStatus_todo_shouldClearCompletedAt() {
        Task task = Task.create("Tâche", "Desc", "user-1")
                .updateStatus(Task.TaskStatus.DONE);

        assertThat(task.completedAt()).isNotNull();

        Task backToTodo = task.updateStatus(Task.TaskStatus.TODO);

        assertThat(backToTodo.status()).isEqualTo(Task.TaskStatus.TODO);
        assertThat(backToTodo.completedAt()).isNull();
    }

    // ============================================================
    // TESTS : CHANGEMENT DE PRIORITÉ
    // ============================================================

    @Test
    @DisplayName("updatePriority() devrait changer uniquement la priorité")
    void updatePriority_shouldChangeOnlyPriority() {
        Task task = Task.create("Tâche", "Desc", "user-1");

        Task critical = task.updatePriority(Task.TaskPriority.CRITICAL);

        assertThat(critical.priority()).isEqualTo(Task.TaskPriority.CRITICAL);
        assertThat(critical.title()).isEqualTo("Tâche");
        assertThat(critical.status()).isEqualTo(Task.TaskStatus.TODO);
        assertThat(critical.id()).isEqualTo(task.id());
    }

    // ============================================================
    // TESTS : DATE D'ÉCHÉANCE
    // ============================================================

    @Test
    @DisplayName("updateDueDate() devrait changer la date d'échéance")
    void updateDueDate_shouldChangeDueDate() {
        Task task = Task.create("Tâche", "Desc", "user-1");

        LocalDate newDate = LocalDate.of(2026, 6, 15);
        Task updated = task.updateDueDate(newDate);

        assertThat(updated.dueDate()).isEqualTo(newDate);
        assertThat(updated.title()).isEqualTo("Tâche");
    }

    // ============================================================
    // TESTS : SOFT DELETE
    // ============================================================

    @Test
    @DisplayName("softDelete() devrait mettre deletedAt et isDeleted() à true")
    void softDelete_shouldSetDeletedAt() {
        Task task = Task.create("Tâche", "Desc", "user-1");

        assertThat(task.isDeleted()).isFalse();

        Task deleted = task.softDelete();

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.deletedAt()).isNotNull();
        // L'original ne doit PAS être modifié
        assertThat(task.isDeleted()).isFalse();
        assertThat(task.deletedAt()).isNull();
    }
}