package com.tasksphere.iam.port.out;

import com.tasksphere.iam.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Port de sortie (outbound port) pour l'accès aux données des refresh tokens.
 *
 * CONCEPT - Déclaration de requêtes avec Spring Data JPA :
 * ======================================================
 * On déclare uniquement l'interface + les signatures de méthodes.
 * Spring Data JPA génère AUTOMATIQUEMENT l'implémentation SQL à l'exécution.
 *
 * CONVENTION DE NOMMAGE :
 * Le nom de la méthode DOIT suivre la convention Spring Data :
 * - findByXxx → SELECT * WHERE xxx = ?
 * - findByXxxAndYyy → SELECT * WHERE xxx = ? AND yyy = ?
 * - findByXxxFalse → SELECT * WHERE xxx = false
 *
 * Exemple : findByUserIdAndRevokedFalse(String userId) sera traduit en :
 * SELECT * FROM refresh_tokens WHERE user_id = ? AND revoked = false
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    /**
     * Récupère tous les tokens actifs (non révoqués) d'un utilisateur.
     * Utilisé pour la déconnexion (révoquer tous les tokens d'un user).
     *
     * Convention : "And" = WHERE ... AND ..., "False" = valeur false du champ boolean
     */
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(String userId);

    /**
     * Supprime tous les tokens d'un utilisateur.
     * Utilisé lors de la suppression d'un compte.
     */
    void deleteByUserId(String userId);

    /**
     * Récupère TOUS les tokens non révoqués (tous utilisateurs confondus).
     *
     * Convention : findByRevokedFalse → SELECT * FROM refresh_tokens WHERE revoked = false
     *
     * Utilisé par RefreshTokenService pour chercher un token par hash BCrypt.
     * On ne peut pas faire de recherche par hash BCrypt directement en SQL
     * (BCrypt utilise un sel aléatoire à chaque hash), donc on récupère
     * tous les tokens actifs et on vérifie le hash en Java avec passwordEncoder.matches().
     */
    List<RefreshTokenEntity> findByRevokedFalse();
}