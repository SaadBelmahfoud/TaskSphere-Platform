package com.tasksphere.iam.config.security;

import com.tasksphere.iam.domain.RefreshTokenEntity;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.RefreshTokenRepository;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service gérant le cycle de vie des refresh tokens.
 *
 * CONCEPT - Dual Token Architecture :
 * ===================================
 * On utilise DEUX types de tokens :
 * 1. Access Token (JWT, 1h) → court terme, stateless, auto-suffisant
 * 2. Refresh Token (UUID opaque, 7j) → long terme, stateful (stocké en base)
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 1 — CORRECTION P0-2 : O(N) → O(1) via SHA-256 token_hash
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (O(N) — CRITIQUE) :
 *   verifyRefreshToken() chargeait TOUS les tokens actifs (findByRevokedFalse())
 *   puis itérait avec BCrypt.matches() sur chaque token.
 *   → Complexité : O(N) × coût BCrypt (~100ms par vérification)
 *   → Avec 100 tokens actifs : ~10 secondes par refresh
 *   → Avec 1000 tokens actifs : ~100 secondes par refresh → TIMEOUT
 *
 * APRÈS (O(1) — OPTIMAL) :
 *   verifyRefreshToken() calcule SHA-256(rawToken) puis fait un
 *   SELECT WHERE token_hash = ? AND revoked = false.
 *   → Complexité : O(1) × coût BCrypt (~100ms, une seule vérification)
 *   → Temps constant indépendant du nombre de tokens en base
 *   → Même avec 1 million de tokens : toujours ~100ms
 *
 * DOUBLE HASH (SHA-256 + BCrypt) :
 * ──────────────────────────────────
 * Pourquoi deux hash ? Chacun sert un but différent :
 *
 * 1. SHA-256 (tokenHash) : INDEX DE RECHERCHE
 *    - Déterministe : même input → même output → indexable en BDD
 *    - Rapide : ~1μs pour calculer
 *    - One-way : impossible de retrouver le token original
 *    - Stocké dans une colonne INDEXÉE (UNIQUE)
 *
 * 2. BCrypt (token) : STOCKAGE SÉCURISÉ
 *    - Non-déterministe : même input → output différent (sel aléatoire)
 *    - Lent par conception : résiste au brute-force
 *    - One-way : impossible de retrouver le token original
 *    - Stocké dans la colonne token existante
 *
 * FLUX DE VÉRIFICATION :
 * ┌───────────────────────────────────────────────────────────┐
 * │ 1. Client envoie rawToken                                 │
 * │ 2. Service calcule SHA-256(rawToken) → tokenHash          │
 * │ 3. SELECT WHERE token_hash = ? AND revoked = false → O(1) │
 * │ 4. Si trouvé → BCrypt.matches(rawToken, storedToken) → 1× │
 * │ 5. Si matches → token valide                              │
 * └───────────────────────────────────────────────────────────┘
 *
 * COMPATIBILITÉ RÉTROACTIVE :
 * ───────────────────────────
 * Les tokens créés AVANT la V7 n'ont pas de tokenHash (NULL).
 * Le service vérifie d'abord via tokenHash (O(1)), et si le token
 * n'est pas trouvé, il fallback sur l'ancienne méthode (O(N)).
 * Ce fallback sera supprimé après que tous les anciens tokens
 * auront expiré naturellement (7 jours max).
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** Durée de vie d'un refresh token en jours */
    private static final int REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    /** Repositories pour l'accès aux données */
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /**
     * PasswordEncoder utilisé pour HASHER le refresh token avant stockage.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P0-2 : Calcul du hash SHA-256 pour lookup O(1)
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE SHA-256 :
     * - Algorithme de hachage cryptographique (NIST FIPS 180-4)
     * - Déterministe : même input → même output → prédictible → INDEXABLE
     * - One-way : impossible de retrouver l'input à partir du hash
     * - Taille fixe : toujours 256 bits = 64 caractères hexadécimaux
     * - Résistant aux collisions : pratiquement impossible que deux inputs
     *   différents produisent le même hash
     *
     * POURQUOI SHA-256 ET PAS SHA-1 OU MD5 ?
     * - MD5 : 128 bits, cassé (collisions démontrées) → DANGER
     * - SHA-1 : 160 bits, cassé (collision pratique en 2017) → DANGER
     * - SHA-256 : 256 bits, sûr (aucune collision pratique) → OK
     * - SHA-512 : 512 bits, sûr mais plus lent → overkill pour un index
     *
     * @param rawToken  Le token brut (UUID) envoyé par le client
     * @return          Le hash SHA-256 en hexadécimal (64 caractères)
     * ═══════════════════════════════════════════════════════════════════
     */
    private String computeTokenHash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            // Conversion en hexadécimal : chaque byte → 2 caractères hex
            StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti disponible dans toute JVM standard
            // Cette exception ne devrait JAMAIS se produire
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Crée un nouveau refresh token pour un utilisateur.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P0-2 : Stockage du tokenHash en plus du BCrypt hash
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : Seul le hash BCrypt était stocké (non-indexable)
     * APRÈS : Les deux hash sont stockés :
     *   - token = BCrypt(rawToken) → stockage sécurisé
     *   - tokenHash = SHA-256(rawToken) → index de recherche O(1)
     * ═══════════════════════════════════════════════════════════════════
     */
    @Transactional
    public String createRefreshToken(String userId) {
        log.info("Création d'un nouveau refresh token pour l'utilisateur: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = passwordEncoder.encode(rawToken);
        String tokenHash = computeTokenHash(rawToken);

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(hashedToken)
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRATION_DAYS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token créé avec succès, expire le: {}", refreshToken.getExpiresAt());
        return rawToken;
    }

    /**
     * Vérifie la validité d'un refresh token.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P0-2 : O(1) lookup via tokenHash + fallback O(N)
     * ═══════════════════════════════════════════════════════════════════
     *
     * STRATÉGIE EN DEUX ÉTAPES :
     *
     * 1. CHEMIN PRINCIPAL (O(1)) — Nouveaux tokens avec tokenHash :
     *    a) Calculer SHA-256(rawToken)
     *    b) SELECT WHERE token_hash = ? AND revoked = false → 0 ou 1 résultat
     *    c) Si trouvé → BCrypt.matches(rawToken, storedToken) → vérification unique
     *    d) Complexité : O(1) index lookup + O(1) BCrypt = TEMPS CONSTANT
     *
     * 2. FALLBACK (O(N)) — Anciens tokens sans tokenHash :
     *    Si le lookup par tokenHash ne retourne rien, on fallback sur
     *    l'ancienne méthode (findByRevokedFalse + itération BCrypt).
     *    Ce fallback est TEMPORAIRE : il sera supprimé après l'expiration
     *    de tous les tokens créés avant la migration V7 (7 jours max).
     *
     * POURQUOI UN FALLBACK ?
     * - Pendant la période de transition, les utilisateurs connectés AVANT
     *   le déploiement de la V7 auront des tokens sans tokenHash.
     * - Sans fallback, ces utilisateurs seraient déconnectés de force.
     * - Le fallback garantit une transition transparente (zero-downtime).
     *
     * ═══════════════════════════════════════════════════════════════════
     * CORRECTIF : @Transactional(readOnly = true)
     * ═══════════════════════════════════════════════════════════════════
     * POURQUOI readOnly ?
     * → Cette méthode ne fait QUE des lectures (SELECT)
     * → Elle ne modifie aucune donnée en base
     * → readOnly permet à Hibernate d'optimiser :
     *   - Pas de dirty checking (pas de snapshot à comparer)
     *   - Connexion en lecture seule (pas de verrouillage)
     *
     * ATTENTION : Avec open-in-view: false, la session est fermée au retour.
     * Le @ManyToOne(fetch = EAGER) sur RefreshTokenEntity.user garantit
     * que l'utilisateur est chargé AVANT la fermeture de la session.
     * ═══════════════════════════════════════════════════════════════════
     */
    @Transactional(readOnly = true)
    public Optional<RefreshTokenEntity> verifyRefreshToken(String rawToken) {
        log.debug("Vérification du refresh token...");

        // ═══════════════════════════════════════════════════════
        // CHEMIN PRINCIPAL (O(1)) : Lookup via tokenHash
        // ═══════════════════════════════════════════════════════
        String tokenHash = computeTokenHash(rawToken);
        Optional<RefreshTokenEntity> foundByHash = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash);

        if (foundByHash.isPresent()) {
            RefreshTokenEntity storedToken = foundByHash.get();
            // Vérification BCrypt : sécurité supplémentaire (défense en profondeur)
            if (passwordEncoder.matches(rawToken, storedToken.getToken())) {
                if (storedToken.isExpired()) {
                    log.warn("Refresh token expiré, id: {}", storedToken.getId());
                    return Optional.empty();
                }
                log.debug("Refresh token valide trouvé via tokenHash (O(1)), id: {}", storedToken.getId());
                return Optional.of(storedToken);
            } else {
                // SHA-256 collision extrêmement improbable mais on logue
                log.warn("SHA-256 match mais BCrypt mismatch pour token_hash: {}", tokenHash);
                return Optional.empty();
            }
        }

        // ═══════════════════════════════════════════════════════
        // FALLBACK (O(N)) : Anciens tokens sans tokenHash
        // ═══════════════════════════════════════════════════════
        // Ce code gère les tokens créés AVANT la migration V7
        // qui n'ont pas encore de tokenHash (NULL en base).
        // TEMPORAIRE : à supprimer après expiration des anciens tokens.
        log.debug("Token non trouvé via tokenHash, fallback O(N) pour compatibilité...");
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository.findByRevokedFalse();

        for (RefreshTokenEntity storedToken : activeTokens) {
            if (passwordEncoder.matches(rawToken, storedToken.getToken())) {
                if (storedToken.isExpired()) {
                    log.warn("Refresh token expiré, id: {}", storedToken.getId());
                    return Optional.empty();
                }
                if (storedToken.isRevoked()) {
                    log.warn("Tentative d'utilisation d'un refresh token révoqué, id: {}", storedToken.getId());
                    return Optional.empty();
                }
                log.debug("Refresh token valide trouvé via fallback O(N), id: {}", storedToken.getId());
                return Optional.of(storedToken);
            }
        }

        log.warn("Aucun refresh token correspondant trouvé");
        return Optional.empty();
    }

    /**
     * Révoque un refresh token spécifique (rotation).
     */
    @Transactional
    public void revokeToken(String tokenId) {
        log.info("Révocation du refresh token: {}", tokenId);
        refreshTokenRepository.findById(tokenId).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /**
     * Révoque TOUS les refresh tokens d'un utilisateur (déconnexion multi-appareils).
     */
    @Transactional
    public void revokeAllUserTokens(String userId) {
        log.info("Révocation de tous les refresh tokens pour l'utilisateur: {}", userId);

        List<RefreshTokenEntity> activeTokens = refreshTokenRepository
                .findByUserIdAndRevokedFalse(userId);

        for (RefreshTokenEntity token : activeTokens) {
            token.setRevoked(true);
        }

        refreshTokenRepository.saveAll(activeTokens);
        log.info("{} token(s) révoqué(s) pour l'utilisateur: {}",
                activeTokens.size(), userId);
    }
}