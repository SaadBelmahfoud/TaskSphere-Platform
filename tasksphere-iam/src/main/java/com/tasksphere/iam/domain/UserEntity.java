package com.tasksphere.iam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * L'ENTITÉ DE L'IDENTITÉ.
 * ATTENTION : Ce n'est PAS le même concept que le DTO UserInfo du module Core !
 * C'est la représentation exacte de la table SQL.
 */
@Entity
@Table(name = "iam_users") // Nom de table spécifique pour ne pas conflituer avec le module Core
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true) // L'username doit être unique en base
    private String username;

    @Column(nullable = false)
    private String password; // Stockera le hash BCrypt, jamais le mot de passe en clair !

    @Column(nullable = false)
    private String role;
}