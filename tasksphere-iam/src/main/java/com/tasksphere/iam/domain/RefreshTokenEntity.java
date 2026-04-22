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
     * Relation vers l'utilisateur propriétaire du token.
     *
     * FetchType.LAZY = l'utilisateur n'est chargé en mémoire que si on y accède
     * (pas automatiquement avec le token). Optimisation mémoire.
     *
     * CascadeType.ALL + orphanRemoval = si l'utilisateur est supprimé,
     * tous ses tokens sont automatiquement supprimés aussi.
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