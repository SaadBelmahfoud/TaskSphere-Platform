package com.tasksphere.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DOMAINE : Comment (Commentaire)
 * ═══════════════════════════════════════════════════════════════════
 *
 * Ce record Java représente un commentaire associé à une tâche.
 * Comme Task (existant), il est IMMUTABLE : chaque modification
 * retourne une NOUVELLE instance.
 *
 * RAPPEL — POURQUOI UN RECORD ET PAS UNE CLASSE ?
 * ──────────────────────────────────────────────────
 * 1. IMMUTABILITÉ : pas d'effets de bord, thread-safe par défaut
 * 2. TRANSPARENCE RÉFÉRENTIELLE : comment.update() ne modifie PAS comment
 * 3. SÉCURITÉ : pas de setters publics → état toujours cohérent
 *
 * NOUVEAU — RELATION AVEC TASK (Agrégat racine existant) :
 * ────────────────────────────────────────────────────
 * Un Comment APPARTIENT à un Task. La relation est 1:N
 * (une tâche peut avoir plusieurs commentaires).
 *
 * Le lien est fait via taskId (ID de la tâche), PAS par référence directe.
 * POURQUOI ?
 * - Évite les dépendances circulaires entre agrégats
 * - En persistance, une référence directe Task task causerait
 *   des problèmes de lazy loading et de sérialisation
 * - L'ID suffit (pattern Identity Map du DDD)
 *
 * NOUVEAU — RBAC POUR LES COMMENTAIRES :
 * ────────────────────────────────────────
 * - LECTURE : tout utilisateur authentifié peut lire les commentaires
 *   d'une tâche qu'il a le droit de voir (même RBAC que Task)
 * - CRÉATION : tout utilisateur peut commenter une tâche visible
 * - MODIFICATION : seul le PROPRIÉTAIRE (username === auteur) peut modifier
 * - SUPPRESSION : seul le PROPRIÉTAIRE ou un ADMIN peut supprimer
 *
 * ARCHITECTURE HEXAGONALE :
 * ─────────────────────────
 * Ce record est dans le DOMAINE (cœur). Il ne dépend d'aucun framework.
 * L'adaptateur CommentEntity fait la traduction vers JPA/Hibernate.
 */
public record Comment(
        String id,              // UUID unique du commentaire
        String content,         // Contenu textuel du commentaire
        String username,        // Email de l'auteur (extrait du JWT)
        String taskId,          // ID de la tâche associée (clé étrangère logique)
        LocalDateTime createdAt, // Date de création (immutable, ne change jamais)
        LocalDateTime updatedAt  // Date de dernière modification
) {

    // ═══════════════════════════════════════════════════════
    // FACTORY METHOD : Création d'un nouveau commentaire
    // ═══════════════════════════════════════════════════════
    // RAPPEL (de Task.java) : Un Factory Method remplace le constructeur
    // public pour donner un nom explicite et gérer les valeurs par défaut.

    /**
     * Crée un nouveau commentaire.
     *
     * @param content  Le contenu textuel (non null, non vide — validé par le contrôleur)
     * @param username L'email de l'auteur (extrait du JWT par le contrôleur)
     * @param taskId   L'ID de la tâche à laquelle le commentaire est rattaché
     * @return Une nouvelle instance Comment avec UUID auto-généré
     *
     * POURQUOI createdAt = updatedAt à la création ?
     * → À la création, il n'y a pas eu de "modification" donc les deux dates
     *   sont identiques. updatedAt changera uniquement lors d'un updateContent().
     */
    public static Comment create(String content, String username, String taskId) {
        LocalDateTime now = LocalDateTime.now();
        return new Comment(
                UUID.randomUUID().toString(),
                content,
                username,
                taskId,
                now,
                now  // createdAt = updatedAt à la création
        );
    }

    // ═══════════════════════════════════════════════════════
    // WITHER : Modification du contenu
    // ═══════════════════════════════════════════════════════
    // RAPPEL (de Task.java) : Un "wither" retourne une NOUVELLE instance
    // du record avec un champ modifié. Le record original est intact.

    /**
     * Met à jour le contenu du commentaire.
     * Retourne une NOUVELLE instance (immutabilité du record).
     *
     * INVARIANTS PRÉSERVÉS :
     * - id : ne change JAMAIS (identifiant stable)
     * - username : l'auteur ne change JAMAIS (traçabilité)
     * - taskId : le rattachement à la tâche ne change JAMAIS
     * - createdAt : la date de création ne change JAMAIS (audit)
     * - updatedAt : mis à jour à "maintenant"
     *
     * @param newContent Le nouveau contenu textuel
     * @return Une NOUVELLE instance Comment avec le contenu mis à jour
     */
    public Comment updateContent(String newContent) {
        return new Comment(
                this.id,
                newContent,
                this.username,
                this.taskId,
                this.createdAt,          // Préservé — la date de création ne change jamais
                LocalDateTime.now()      // Mis à jour — traçabilité de la modification
        );
    }
}