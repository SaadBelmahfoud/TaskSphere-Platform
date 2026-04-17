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
}