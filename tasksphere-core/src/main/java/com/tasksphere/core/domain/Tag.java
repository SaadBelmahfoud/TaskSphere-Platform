package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : Tag (Label de catégorisation)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 3 : Système de tags/labels
 * ─────────────────────────────────────────────────
 *
 * PRINCIPE — TAG vs STATUS vs PRIORITY :
 * ────────────────────────────────────────
 * - Status (TODO/DOING/DONE) : cycle de vie de la tâche (FIXE)
 * - Priority (LOW→CRITICAL)  : urgence de la tâche (FIXE)
 * - Tag                      : catégorisation LIBRE par l'utilisateur
 *
 * Les tags permettent de regrouper les tâches par thème, projet,
 * technologie, type de travail, etc.
 *
 * EXEMPLES DE TAGS :
 * - "Bug" (rouge)         → tâches liées à des bugs
 * - "Feature" (vert)      → nouvelles fonctionnalités
 * - "Backend" (bleu)      → travail côté serveur
 * - "Frontend" (violet)   → travail côté client
 * - "Urgent" (orange)     → priorité supplémentaire
 * - "Documentation" (gris) → travail de documentation
 *
 * PROPRIÉTÉS :
 * ──────────────
 * - name    : nom du tag (unique, insensible à la casse)
 * - color   : couleur hexadécimale (#RRGGBB) pour l'affichage
 * - createdBy : créateur du tag (email)
 *
 * ARCHITECTURE HEXAGONALE :
 * ─────────────────────────
 * Ce record est dans le DOMAINE (cœur). Pas de dépendance framework.
 */
public record Tag(
        String id,              // UUID unique
        String name,            // Nom du tag (unique, insensible à la casse)
        String color,           // Couleur hexadécimale (#RRGGBB)
        String createdBy,       // Email du créateur
        LocalDateTime createdAt // Date de création
) {

    /** Couleur par défaut si aucune couleur n'est spécifiée. */
    public static final String DEFAULT_COLOR = "#6B7280"; // Gris

    /**
     * Factory Method : Crée un nouveau tag.
     *
     * @param name      Nom du tag (sera stocké en minuscules pour l'unicité)
     * @param color     Couleur hexadécimale (null → DEFAULT_COLOR)
     * @param createdBy Email du créateur
     * @return Une nouvelle instance Tag
     */
    public static Tag create(String name, String color, String createdBy) {
        return new Tag(
                UUID.randomUUID().toString(),
                name.trim(),                          // Supprimer les espaces
                (color != null && !color.isBlank()) ? color : DEFAULT_COLOR,
                createdBy,
                LocalDateTime.now()
        );
    }

    /**
     * Factory Method : Crée un tag avec couleur par défaut.
     */
    public static Tag create(String name, String createdBy) {
        return create(name, null, createdBy);
    }
}