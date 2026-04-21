package com.tasksphere.iam.port.out;

import com.tasksphere.iam.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Port de sortie (outbound port) pour l'accès aux données utilisateur.
 *
 * CONCEPT - JpaRepository :
 * =========================
 * JpaRepository<Entity, IdType> fournit AUTOMATIQUEMENT toutes les
 * opérations CRUD de base : save(), findById(), findAll(), delete(), etc.
 *
 * On n'écrit AUCUNE implémentation SQL : Spring Data JPA la génère automatiquement
 * à partir des noms de méthodes (query derivation).
 *
 * Exemples :
 * - findByEmail(String email) → SELECT * FROM users WHERE email = ?
 * - existsByEmail(String email) → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 *
 * ====================================================================
 * CORRECTION — Ajout de existsByUsername()
 * ====================================================================
 *
 * PROBLÈME AVANT :
 *   UserRepository ne déclarait que findByEmail() et existsByEmail().
 *   Mais AuthController.register() appelait aussi existsByUsername()
 *   pour vérifier que le nom d'utilisateur n'est pas déjà pris.
 *   → Erreur : "cannot find symbol: method existsByUsername(java.lang.String)"
 *
 * SOLUTION :
 *   Ajout de existsByUsername() avec la même convention Spring Data.
 *
 * CONVENTION DE NOMMAGE SPRING DATA :
 * existsByXxx → SELECT COUNT(*) > 0 FROM table WHERE xxx = ?
 * Spring Data JPA génère automatiquement la requête SQL correspondante.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /**
     * Recherche un utilisateur par son adresse email.
     * Spring Data JPA génère automatiquement la requête SQL correspondante.
     *
     * @param email l'email à rechercher
     * @return Optional contenant l'utilisateur si trouvé
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Vérifie si un utilisateur existe avec cet email.
     * Utilisé lors de l'inscription pour éviter les doublons.
     *
     * @param email l'email à vérifier
     * @return true si un utilisateur avec cet email existe déjà
     */
    boolean existsByEmail(String email);

    /**
     * ← CORRECTION — Vérifie si un utilisateur existe avec ce nom d'utilisateur.
     *
     * Utilisé lors de l'inscription pour éviter les doublons de username.
     * Convention Spring Data : existsByUsername → SELECT COUNT(*) > 0 WHERE username = ?
     *
     * @param username le nom d'utilisateur à vérifier
     * @return true si un utilisateur avec ce username existe déjà
     */
    boolean existsByUsername(String username);
}