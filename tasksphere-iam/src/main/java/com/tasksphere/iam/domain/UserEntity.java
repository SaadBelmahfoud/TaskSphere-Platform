package com.tasksphere.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
 * ====================================================================
 * ENTITÉ UTILISATEUR (Représentation exacte de la table SQL "iam_users")
 * ====================================================================
 *
 * PRINCIPE JPA :
 * Chaque champ annoté @Column correspond à une colonne SQL.
 * @Entity dit à Hibernate : "Crée/mets à jour la table SQL pour qu'elle corresponde à cette classe".
 *
 * SPRING 3 = JAKARTA :
 * On utilise jakarta.persistence.* (pas javax.*). C'est une rupture obligatoire depuis Spring Boot 3.
 *
 * ====================================================================
 * CORRECTION — Ajout de @Setter
 * ====================================================================
 *
 * PROBLÈME AVANT :
 *   UserEntity n'avait que @Getter. AuthController.register() utilisait des setters
 *   (newUser.setUsername(), newUser.setFirstName(), etc.) qui n'existaient PAS.
 *   → Erreur : "cannot find symbol: method setUsername(java.lang.String)"
 *   → Cela faisait échouer TOUTE la compilation du module IAM,
 *     causant des erreurs en cascade sur JwtService, RefreshTokenService, etc.
 *
 * SOLUTION :
 *   Ajout de @Setter pour que Lombok génère tous les setters nécessaires.
 *
 *   NOTE : Le DataInitializer utilise le constructeur @AllArgsConstructor
 *   (new UserEntity(null, username, ...)), donc il n'a JAMAIS eu besoin de setters.
 *   AuthController.register() utilise le constructeur par défaut @NoArgsConstructor
 *   puis les setters, ce qui nécessite @Setter.
 *
 * ====================================================================
 * CORRECTION SPRINT 5 — Cohérence avec Flyway V1 + ddl-auto: validate
 * ====================================================================
 *
 * PROBLÈME :
 *   1. Les longueurs @Column ne correspondaient PAS aux colonnes Flyway V1 :
 *      - username : JPA 255 (défaut) vs Flyway VARCHAR(50) → INSERT > 50 chars = ERREUR PostgreSQL
 *      - role : JPA 255 vs Flyway VARCHAR(20) → INSERT > 20 chars = ERREUR
 *      - firstName : JPA 255 vs Flyway VARCHAR(100) → OK mais incohérent
 *      - lastName : JPA 255 vs Flyway VARCHAR(100) → OK mais incohérent
 *      - avatarUrl : JPA 255 vs Flyway VARCHAR(500) → URL > 255 chars = TRONQUÉE
 *
 *   2. Les colonnes created_at et last_login existent dans Flyway V1 mais
 *      n'ont PAS de champ correspondant dans UserEntity → colonnes orphelines
 *
 * SOLUTION :
 *   - Ajout de @Column(length = ...) cohérent avec Flyway V1
 *   - Ajout des champs createdAt et lastLogin correspondant aux colonnes Flyway
 *   - @PrePersist sur createdAt pour le valoriser automatiquement avant l'INSERT
 */
@Entity
@Table(name = "iam_users")
@Getter          // ← Génère tous les getters (getId(), getUsername(), getEmail(), etc.)
@Setter          // ← CORRECTION : Génère tous les setters (setUsername(), setEmail(), etc.)
@NoArgsConstructor  // ← Génère un constructeur vide (requis par JPA/Hibernate)
@AllArgsConstructor // ← Génère un constructeur avec tous les champs (utilisé par DataInitializer)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 50)   // ← SPRINT 5 : Cohérent avec Flyway V1 VARCHAR(50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // Toujours un hash BCrypt, JAMAIS le mot de passe en clair !

    @Column(nullable = false, length = 20)                  // ← SPRINT 5 : Cohérent avec Flyway V1 VARCHAR(20)
    private String role; // "USER", "MANAGER" ou "ADMIN"

    @Column(length = 100)                                   // ← SPRINT 5 : Cohérent avec Flyway V1 VARCHAR(100)
    private String firstName;

    @Column(length = 100)                                   // ← SPRINT 5 : Cohérent avec Flyway V1 VARCHAR(100)
    private String lastName;

    @Column(length = 500)                                   // ← SPRINT 5 : Cohérent avec Flyway V1 VARCHAR(500)
    private String avatarUrl;

    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * Date de création du compte.
     * CORRECTION SPRINT 5 : Champ ajouté pour correspondre à la colonne
     * created_at dans la migration Flyway V1.
     * @PrePersist valorise automatiquement cette date avant l'INSERT.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Date de dernière connexion.
     * CORRECTION SPRINT 5 : Champ ajouté pour correspondre à la colonne
     * last_login dans la migration Flyway V1.
     * Nullable car un utilisateur peut ne s'être jamais connecté.
     * Peut être mis à jour par le service d'authentification après un login réussi.
     */
    @Column
    private LocalDateTime lastLogin;

    /**
     * Callback JPA appelé AVANT le premier INSERT en base.
     * Valorise createdAt automatiquement.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}