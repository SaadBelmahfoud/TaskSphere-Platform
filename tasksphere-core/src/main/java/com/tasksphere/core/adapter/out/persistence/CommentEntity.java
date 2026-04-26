package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Comment;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : CommentEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * RAPPEL (de TaskEntity.java) :
 * ────────────────────────────
 * Ce traducteur convertit le domaine (Comment record) en entité JPA
 * compréhensible par Hibernate. Il se trouve dans adapter/out/persistence.
 *
 * RELATION DOMAIN ↔ ENTITY (même pattern que TaskEntity) :
 * ┌──────────────────────────────────────────────────────────┐
 * │  Domaine (Comment record)    Entity (CommentEntity)        │
 * │  ──────────────────────      ────────────────────         │
 * │  Immutable                    Mutable (setters)            │
 * │  Pas d'annotations JPA        @Entity, @Table, @Id         │
 * │  Factory Method create()      Constructeurs + toDomain()   │
 * │  Wither updateContent()       setContent() setter          │
 * └──────────────────────────────────────────────────────────┘
 *
 * NOUVEAU — DIFFÉRENCE AVEC TaskEntity :
 * ────────────────────────────────────────
 * CommentEntity n'utilise PAS le chemin dual (isNew + Persistable)
 * car les commentaires sont plus simples :
 * - INSERT : new CommentEntity(comment)
 * - UPDATE : findById() → setContent() + dirty checking
 * - DELETE :.deleteById() → suppression physique (pas de soft delete)
 *
 * Le chemin dual n'est PAS nécessaire car on n'a pas le problème
 * NonUniqueObjectException : les commentaires sont gérés indépendamment
 * des tâches, et le CommentPersistenceAdapter gère explicitement
 * le INSERT vs UPDATE via findById().
 *
 * TABLE SQL : comments
 * Colonnes : id, content, username, task_id, created_at, updated_at
 */
@Entity
@Table(name = "comments")
public class CommentEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** Contenu textuel du commentaire (TEXT pour supporter les longs commentaires) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Email de l'auteur du commentaire.
     * Stocké en String (pas de FK vers iam_users) car le username
     * vient du JWT et est utilisé directement pour l'affichage et le RBAC.
     */
    @Column(nullable = false, length = 100)
    private String username;

    /**
     * ID de la tâche associée.
     * Stocké en String (pas de @ManyToOne) car on utilise le pattern
     * Identity Map : on ne référence que l'ID, pas l'objet entier.
     * Cela évite les problèmes de lazy loading et les requêtes JOIN.
     */
    @Column(nullable = false, length = 36)
    private String taskId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ═══════════════════════════════════════════════════════
    // CONSTRUCTEURS
    // ═══════════════════════════════════════════════════════

    /** Constructeur par défaut requis par JPA/Hibernate. */
    protected CommentEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Constructeur de conversion Domain → Entity.
     * Utilisé pour les NOUVEAUX commentaires (INSERT).
     */
    public CommentEntity(Comment comment) {
        this.id = comment.id();
        this.content = comment.content();
        this.username = comment.username();
        this.taskId = comment.taskId();
        this.createdAt = comment.createdAt();
        this.updatedAt = comment.updatedAt();
    }

    // ═══════════════════════════════════════════════════════
    // MAPPING ENTITY → DOMAIN
    // ═══════════════════════════════════════════════════════

    /** Convertit l'entité JPA en objet du domaine (Comment record). */
    public Comment toDomain() {
        return new Comment(
                this.id, this.content, this.username, this.taskId,
                this.createdAt, this.updatedAt
        );
    }

    // ═══════════════════════════════════════════════════════
    // GETTERS ET SETTERS (dirty checking Hibernate)
    // ═══════════════════════════════════════════════════════
    // RAPPEL : Chaque setter met à jour updatedAt automatiquement.
    // Hibernate détecte les changements (dirty checking) et
    // génère l'UPDATE SQL au commit de la transaction.

    public String getId() { return id; }
    public String getContent() { return content; }
    public String getUsername() { return username; }
    public String getTaskId() { return taskId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}