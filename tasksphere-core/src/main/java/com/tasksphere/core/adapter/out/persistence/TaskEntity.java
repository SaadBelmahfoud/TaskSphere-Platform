package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TaskEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Traduire le domaine (Task) en entité JPA compréhensible par Hibernate.
 *
 * ARCHITECTURE : Cet adaptateur se trouve dans "adapter/out/persistence"
 * car il ADAPTE la sortie du domaine vers la base de données.
 *
 * RELATION DOMAIN ↔ ENTITY :
 * ┌──────────────────────────────────────────────────────────┐
 * │  Domaine (Task record)          Entity (TaskEntity)       │
 * │  ─────────────────────          ────────────────────      │
 * │  Immutable                      Mutable (setters)         │
 * │  Pas d'annotations JPA          @Entity, @Table, @Id      │
 * │  Pas de notion de "new/existing" Persistable.isNew()      │
 * │  Appelle toDomain()              Appelle constructeur     │
 * │                                  ou setters (dirty check) │
 * └──────────────────────────────────────────────────────────┘
 *
 * SOLUTION AU BUG NonUniqueObjectException :
 * ────────────────────────────────────────
 * Problème : Si on fait new TaskEntity(task) puis em.persist(),
 * Hibernate peut lever NonUniqueObjectException car une autre
 * instance avec le même ID existe déjà dans le L1 Cache.
 *
 * Solution (chemin dual dans TaskPersistenceAdapter.save()) :
 * 1. Si l'entité existe en DB → récupérer via findById()
 *    → modifier via setters → Hibernate dirty checking → UPDATE
 * 2. Si nouvelle → new TaskEntity(task) avec isNew=true → INSERT
 *
 * IMPORTANT : @Transient pour isNew
 * ─────────────────────────────────────
 * Le champ `isNew` est annoté @Transient pour que Hibernate
 * NE le persiste PAS en base de données. C'est un champ technique
 * qui existe uniquement en mémoire pour indiquer à Spring Data JPA
 * s'il faut faire un INSERT ou un UPDATE.
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

    /**
     * SOFT DELETE : deletedAt = null → tâche active
     * deletedAt = datetime → tâche archivée
     * Toutes les requêtes doivent filtrer WHERE deletedAt IS NULL
     */
    @Column
    private LocalDateTime deletedAt;

    /**
     * userId = l'email du créateur de la tâche.
     * C'est le champ de "propriété" utilisé pour le RBAC :
     * un USER ne peut voir/modifier que ses propres tâches (userId = son email).
     */
    @Column(nullable = false, length = 36)
    private String userId;

    /**
     * assigneeId = l'email de la personne à qui la tâche est assignée.
     * Option A d'assignation : champ séparé du userId.
     * - null = tâche non assignée
     * - "email@x.com" = tâche assignée à cet utilisateur
     *
     * Permissions :
     * - Seuls ADMIN et MANAGER peuvent modifier ce champ (via /assign)
     * - L'assignataire peut changer le statut de la tâche
     */
    @Column(length = 36)
    private String assigneeId;

    /**
     * CHAMP TECHNIQUE @Transient : isNew
     * ─────────────────────────────────────
     * Ce champ n'est PAS persisté en base de données.
     * Il indique à Spring Data JPA si l'entité est NOUVELLE (INSERT)
     * ou EXISTANTE (UPDATE).
     *
     * Cycle de vie :
     * 1. Constructeur vide (Hibernate) → isNew = false
     * 2. Constructeur Task(Task) → isNew = true
     * 3. @PostPersist/@PostLoad → isNew = false
     *
     * Le @PostLoad s'exécute après chaque SELECT, donc après
     * un findById(), l'entité sera isNew = false → UPDATE.
     */
    @Transient
    private boolean isNew = true;

    /**
     * Constructeur par défaut requis par JPA/Hibernate.
     * Hibernate utilise ce constructeur via réflexion pour
     * créer des instances lors des SELECT.
     */
    protected TaskEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isNew = false;  // Entité chargée depuis DB = pas nouvelle
    }

    /**
     * Constructeur de conversion Domain → Entity.
     * Utilisé pour les NOUVELLES tâches (INSERT).
     *
     * Pour les mises à jour (UPDATE), le TaskPersistenceAdapter
     * utilise le chemin dual : récupérer l'entité existante
     * et modifier via setters (dirty checking).
     */
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
        this.assigneeId = task.assigneeId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isNew = true;  // ← Indique à JPA de faire un INSERT
    }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    /**
     * Cycle de vie JPA : @PostPersist et @PostLoad
     * ──────────────────────────────────────────────
     * @PostPersist : appelé APRÈS l'INSERT en base.
     *   → L'entité existe maintenant en DB → plus nouvelle.
     *
     * @PostLoad : appelé APRÈS chaque SELECT (findById, findAll, etc.).
     *   → L'entité est chargée depuis DB → pas nouvelle → UPDATE si modifiée.
     *
     * SANS cet appel, après un findById() l'entité aurait encore isNew=true
     * et Hibernate tenterait un INSERT au lieu d'un UPDATE → erreur !
     */
    @PostPersist
    @PostLoad
    private void markNotNew() { this.isNew = false; }

    /**
     * Convertit l'entité JPA en objet du domaine (Task record).
     * C'est la méthode de mappage Entity → Domain.
     */
    public Task toDomain() {
        return new Task(this.id, this.title, this.description, this.status, this.priority,
                this.dueDate, this.completedAt, this.deletedAt, this.userId, this.assigneeId);
    }

    // ═══════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════

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
    public String getAssigneeId() { return assigneeId; }

    // ═══════════════════════════════════════════════════════
    // SETTERS (utilisés par le dirty checking de Hibernate)
    // ═══════════════════════════════════════════════════════
    // Chaque setter met à jour updatedAt automatiquement.
    // C'est le dirty checking : Hibernate compare l'état actuel
    // de l'entité avec son snapshot (pris au chargement).
    // Si un champ a changé → Hibernate génère un UPDATE au flush.

    public void setTitle(String title) { this.title = title; this.updatedAt = LocalDateTime.now(); }
    public void setDescription(String description) { this.description = description; this.updatedAt = LocalDateTime.now(); }
    public void setStatus(Task.TaskStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public void setPriority(Task.TaskPriority priority) { this.priority = priority; this.updatedAt = LocalDateTime.now(); }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; this.updatedAt = LocalDateTime.now(); }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; this.updatedAt = LocalDateTime.now(); }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; this.updatedAt = LocalDateTime.now(); }

    /**
     * Setter pour l'assignation de tâche.
     * Utilisé par TaskPersistenceAdapter lors de l'assignation.
     */
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; this.updatedAt = LocalDateTime.now(); }
}