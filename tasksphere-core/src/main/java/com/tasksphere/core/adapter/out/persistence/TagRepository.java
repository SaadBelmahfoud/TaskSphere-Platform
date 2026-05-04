package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * REPOSITORY JPA : TagRepository
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Repository pour les tags
 * ─────────────────────────────────────────────────
 */
@Repository
public interface TagRepository extends JpaRepository<TagEntity, String> {

    /**
     * Trouver un tag par nom, insensible à la casse.
     * SQL : SELECT * FROM tags WHERE LOWER(name) = LOWER(:name)
     */
    Optional<TagEntity> findByNameIgnoreCase(String name);
}