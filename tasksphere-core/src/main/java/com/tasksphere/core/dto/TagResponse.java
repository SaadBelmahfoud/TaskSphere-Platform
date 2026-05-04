package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Tag;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse Tag (Ce que le client reçoit)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : DTO pour les tags/labels
 * ─────────────────────────────────────────────────
 */
public record TagResponse(
        String id,
        String name,
        String color,
        String createdBy,
        LocalDateTime createdAt
) {

    public static TagResponse fromDomain(Tag tag) {
        return new TagResponse(
                tag.id(),
                tag.name(),
                tag.color(),
                tag.createdBy(),
                tag.createdAt()
        );
    }
}