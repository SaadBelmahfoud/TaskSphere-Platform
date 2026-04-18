package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * ====================================================================
 * ENTITÉ JPA : TASK (Représentation exacte de la table SQL "tasks")
 * ====================================================================
 *
 * PRINCIPE JPA :
 * Cette classe est le pont entre le monde objet (Java) et le monde relationnel (SQL).
 * Hibernate lit cette classe et génère automatiquement les requêtes SQL
 * pour créer/mettre à jour la table "tasks".
 *
 * SPRINT 1 - AJOUTS :
 * - status, priority : enums stockés comme VARCHAR en SQL
 * - dueDate : date d'échéance optionnelle
 * - completedAt, updatedAt : timestamps automatiques
 * - deletedAt : soft delete (null = actif, non-null = archivé)
 * - userId : lien vers le propriétaire (ownership)
 *
 * PRINCIPE @Enumerated(EnumType.STRING) :
 * On stocke l'enum comme texte ("TODO") et pas comme ordinal (0, 1, 2).
 * Pourquoi ? Si on ajoute une valeur au milieu de l'enum, les ordinaux changent
 * et toutes les données existantes seraient corrompues.
 *
 * PRINCIPE Persistable<String> :
 * L'UUID est pré-généré dans le domaine (Task.create → UUID.randomUUID).
 * Sans Persistable, Spring Data voit un ID non-null et pense que l'entité
 * est déjà en base → il fait un merge() au lieu de persist() → crash.
 * Persistable.isNew() indique explicitement si c'est une nouvelle entité.
 */
@Entity
@Table(name = "tasks")
public class TaskEntity implements Persistable<String> {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Task.TaskStatus status = Task.TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Task.TaskPriority priority = Task.TaskPriority.MEDIUM;

    @Column
    private LocalDate dueDate;

    @Column
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false, length = 36)
    private String userId;

    /**
     * Flag transient pour indiquer à Spring Data JPA si c'est une nouvelle entité.
     * - true  : → repository.save() fera un em.persist()  (INSERT)
     * - false : → repository.save() fera un em.merge()    (UPDATE)
     */
    @Transient
    private boolean isNew = true;

    // ============ CONSTRUCTEURS ============

    /** Constructeur vide obligatoire pour JPA (crée un objet depuis les rows SQL) */
    protected TaskEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isNew = false;
    }

    /** Constructeur pour créer une nouvelle tâche depuis le domaine */
    public TaskEntity(Task task) {
        this.id = task.id();
        this.title = task.title();
        this.description = task.description();
        this.status = task.status();
        this.priority = task.priority();
        this.dueDate = task.dueDate();
        this.completedAt = task.completedAt();
        this.deletedAt = task.deletedAt();
        this.userId = task.userId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isNew = true;
    }

    // ============ Persistable<String> ============

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /** Après INSERT en base, l'entité n'est plus "nouvelle" */
    @PostPersist
    @PostLoad
    private void markNotNew() {
        this.isNew = false;
    }

    // ============ CONVERSION VERS DOMAINE ============

    /**
     * Convertit cette entité JPA en objet domaine Task.
     * C'est la SEULE méthode qui traverse la frontière JPA → Domaine.
     */
    public Task toDomain() {
        return new Task(
                this.id,
                this.title,
                this.description,
                this.status,
                this.priority,
                this.dueDate,
                this.completedAt,
                this.deletedAt,
                this.userId
        );
    }

    // ============ GETTERS ============

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Task.TaskStatus getStatus() { return status; }
    public Task.TaskPriority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getUserId() { return userId; }

    // ============ SETTERS ============
    // Utilisés par l'adaptateur de persistance pour les mises à jour partielles.
    // Chaque setter met aussi à jour updatedAt automatiquement.

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void setStatus(Task.TaskStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPriority(Task.TaskPriority priority) {
        this.priority = priority;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.updatedAt = LocalDateTime.now();
    }
}