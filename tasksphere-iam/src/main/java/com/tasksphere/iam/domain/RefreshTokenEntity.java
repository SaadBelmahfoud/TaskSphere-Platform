package com.tasksphere.iam.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un refresh token stocké en base de données.
 *
 * CONCEPT - Pourquoi un Refresh Token en base ?
 * ============================================
 * Les refresh tokens sont "opaques" (contrairement aux JWT access tokens qui sont auto-suffisants).
 * On les stocke en base pour pouvoir :
 * - Les révoquer individuellement (logout)
 * - Les révoquer tous (déconnexion de toutes les sessions)
 * - Implémenter la rotation (un nouveau token est créé à chaque refresh)
 * - Détecter les vols (si un token révoqué est réutilisé)
 *
 * CONCEPT - Lombok annotations :
 * ============================================
 * @Getter  → génère tous les getters (getId(), getToken(), getRevoked(), etc.)
 * @Setter  → génère tous les setters (setRevoked(), setExpiresAt(), etc.)
 * @NoArgsConstructor → génère un constructeur vide (requis par JPA/Hibernate)
 * @AllArgsConstructor → génère un constructeur avec tous les champs
 * @Builder → génère un pattern Builder pour une construction fluide de l'objet
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION SPRINT 5 : @Column(length = 36) sur l'ID
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 *   Sans @Column(length = 36), Hibernate suppose que l'ID est VARCHAR(255).
 *   Or la migration Flyway V2 crée la colonne id en VARCHAR(36).
 *   Avec ddl-auto: validate, Hibernate compare le schéma attendu (255)
 *   avec le schéma réel (36) → SchemaValidationException → CRASH au démarrage.
 *
 * SOLUTION :
 *   Ajouter @Column(length = 36) pour que Hibernate attende VARCHAR(36),
 *   cohérent avec le script Flyway V2 et les autres entités (UserEntity,
 *   TaskEntity, CommentEntity, ActivityLogEntity qui ont toutes cette annotation).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)  // ← CORRECTION SPRINT 5 : Cohérent avec Flyway V2 (VARCHAR(36))
    private String id;

    /**
     * Le token est stocké HACHÉ (BCrypt).
     * ON NE STOCKE JAMAIS UN TOKEN EN CLAIR EN BASE DE DONNÉES.
     *
     * Pourquoi ? Si la base est compromise, l'attaquant ne peut pas
     * réutiliser les tokens volés (il lui faudrait casser le hash BCrypt).
     * Même principe que le stockage des mots de passe.
     */
    @Column(nullable = false, length = 255, unique = true)
    private String token;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — CORRECTION P0-2 : Hash SHA-256 pour lookup O(1)
     * ═══════════════════════════════════════════════════════════════════
     *
     * PROBLÈME :
     *   BCrypt est non-déterministe (sel aléatoire) → impossible d'indexer
     *   → verifyRefreshToken() doit charger TOUS les tokens et itérer → O(N)
     *
     * SOLUTION :
     *   Stocker un hash SHA-256 (déterministe) du token brut.
     *   SHA-256 produit toujours le même hash pour le même input → indexable.
     *   Au refresh : SHA-256(rawToken) → SELECT WHERE token_hash = ? → O(1)
     *
     * SÉCURITÉ :
     *   - SHA-256 est une fonction à sens unique → impossible de retrouver
     *     le token original à partir du hash
     *   - Le token brut est un UUID (128 bits d'entropie) → attaque par
     *     dictionnaire impossible
     *   - Même si un attaquant obtient token_hash, il ne peut pas forger
     *     un token valide
     *
     * @Column(nullable = false) serait idéal mais la migration V7
     * permet temporairement les NULL pour les tokens existants.
     * Les NOUVEAUX tokens auront toujours un tokenHash non-null.
     * ═══════════════════════════════════════════════════════════════════
     */
    @Column(length = 64, unique = true)
    private String tokenHash;

    /**
     * Relation vers l'utilisateur propriétaire du token.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CORRECTION CRITIQUE : FetchType.EAGER (pas LAZY)
     * ═══════════════════════════════════════════════════════════════════
     *
     * POURQUOI EAGER ?
     * ─────────────────
     * 1. open-in-view: false dans application.yaml
     *    → La session Hibernate est fermée à la fin de chaque transaction
     *    → L'accès à storedToken.getUser() hors transaction provoque
     *      LazyInitializationException
     *
     * 2. AuthController.refresh() fait :
     *    a) verifyRefreshToken() → @Transactional(readOnly=true) → session fermée au retour
     *    b) storedToken.getUser() → HORS transaction → LazyInitializationException !
     *
     * 3. Avec EAGER, Hibernate charge l'utilisateur IMMÉDIATEMENT
     *    lors du SELECT du refresh token → pas de lazy loading → pas d'exception
     *
     * COÛT NÉGLIGEABLE :
     * - Un refresh token a exactement UN utilisateur (relation @ManyToOne)
     * - Pas de problème N+1 (un SELECT par refresh token, pas par collection)
     * - Le refresh endpoint n'est pas appelé souvent (seulement quand le JWT expire)
     *
     * CASCADE + ORPHAN REMOVAL :
     * Si l'utilisateur est supprimé, tous ses tokens sont automatiquement supprimés.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Date d'expiration du refresh token.
     * Par défaut, on fixe 7 jours après la création.
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Indicateur de révocation.
     * - false = token actif et utilisable pour un refresh
     * - true = token révoqué (suite à un logout ou une rotation)
     *
     * Quand un token est révoqué, il NE PEUT PLUS être utilisé.
     * C'est ce qui permet de gérer les déconnexions proprement.
     */
    @Column(nullable = false)
    private boolean revoked;

    /**
     * Date de création du token.
     * updatable = false → Hibernate ne modifie jamais cette colonne après insertion.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Callback JPA appelé automatiquement AVANT la première insertion en base.
     * Permet de valoriser createdAt automatiquement sans avoir à le faire
     * manuellement dans le service. C'est un "lifecycle callback".
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Vérifie si le token est expiré (date d'expiration dépassée).
     * @return true si le token est expiré
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}