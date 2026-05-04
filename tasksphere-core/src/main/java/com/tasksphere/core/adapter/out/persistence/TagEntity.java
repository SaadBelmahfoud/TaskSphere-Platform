package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Tag;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TagEntity
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Entité JPA pour les tags
 * ─────────────────────────────────────────────────
 */
@Entity
@Table(name = "tags")
public class TagEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(nullable = false, length = 255)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected TagEntity() {}

    public TagEntity(Tag tag) {
        this.id = tag.id();
        this.name = tag.name();
        this.color = tag.color();
        this.createdBy = tag.createdBy();
        this.createdAt = tag.createdAt();
    }

    public Tag toDomain() {
        return new Tag(id, name, color, createdBy, createdAt);
    }

    // Getters et Setters pour JPA
    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setName(String name) { this.name = name; }
    public void setColor(String color) { this.color = color; }
}