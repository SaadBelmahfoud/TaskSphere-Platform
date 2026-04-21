package com.tasksphere.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // Toujours un hash BCrypt, JAMAIS le mot de passe en clair !

    @Column(nullable = false)
    private String role; // "USER", "MANAGER" ou "ADMIN"

    @Column
    private String firstName;

    @Column
    private String lastName;

    @Column
    private String avatarUrl;

    @Column(nullable = false)
    private Boolean enabled = true;
}