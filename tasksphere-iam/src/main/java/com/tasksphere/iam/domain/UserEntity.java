package com.tasksphere.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
 */
@Entity
@Table(name = "iam_users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
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