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
 * findByUserIdAndRevokedFalse(String userId) sera traduit en :
 * SELECT * FROM refresh_tokens WHERE user_id = ? AND revoked = false
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    /**
     * Récupère tous les tokens actifs (non révoqués) d'un utilisateur.
     * Utilisé pour la déconnexion (révoquer tous les tokens d'un user).
     *
     * Convention Spring Data : "And" = WHERE ... AND ..., "False" = false
     *
     * @param userId l'ID de l'utilisateur
     * @return liste des tokens actifs
     */
    List<RefreshTokenEntity> findByUserIdAndRevokedFalse(String userId);

    /**
     * Supprime tous les tokens d'un utilisateur.
     * Utilisé lors de la suppression d'un compte.
     *
     * @param userId l'ID de l'utilisateur
     */
    void deleteByUserId(String userId);

    /**
     * Récupère tous les tokens actifs (non révoqués et non expirés).
     * Utilisé par RefreshTokenService pour chercher un token par hash BCrypt
     * (on ne peut pas faire de recherche par hash directement en SQL).
     *
     * @return liste de tous les tokens actifs
     */
    List<RefreshTokenEntity> findAllActiveTokens();
}