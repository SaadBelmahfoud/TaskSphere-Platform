package com.tasksphere.iam.port.out;

import com.tasksphere.iam.domain.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — CORRECTION P0-2 : Cette méthode est DÉPRÉCIÉE
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : Utilisée par verifyRefreshToken() pour charger TOUS les tokens
     * actifs et itérer avec BCrypt.matches() → O(N) × 100ms = CATASTROPHIQUE
     *
     * APRÈS : Remplacée par findByTokenHashAndRevokedFalse() → O(1)
     * Cette méthode est conservée pour la rétrocompatibilité avec les
     * anciens tokens qui n'ont pas encore de tokenHash, mais elle ne
     * devrait plus être utilisée dans le flux nominal.
     *
     * @deprecated Utiliser findByTokenHashAndRevokedFalse() à la place
     * ═══════════════════════════════════════════════════════════════════
     */
    @Deprecated
    List<RefreshTokenEntity> findByRevokedFalse();

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — CORRECTION P0-2 : Lookup O(1) via token_hash
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE : SHA-256 est déterministe → même token → même hash
     * → On peut faire un SELECT WHERE token_hash = ? AND revoked = false
     * → L'index idx_refresh_tokens_token_hash (UNIQUE) garantit O(1)
     *
     * TRADUCTION SQL AUTOMATIQUE (Spring Data) :
     * SELECT * FROM refresh_tokens
     * WHERE token_hash = ? AND revoked = false
     * → Utilise l'index idx_refresh_tokens_token_hash
     * → Retourne 0 ou 1 résultat (UNIQUE constraint)
     *
     * @param tokenHash  Le hash SHA-256 du token brut envoyé par le client
     * @return Optional contenant le token s'il est trouvé et actif
     * ═══════════════════════════════════════════════════════════════════
     */
    Optional<RefreshTokenEntity> findByTokenHashAndRevokedFalse(String tokenHash);
}