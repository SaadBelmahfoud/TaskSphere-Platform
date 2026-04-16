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
 * Pourquoi ?
 * - Si un access token est volé, l'attaquant n'a que 1h pour l'utiliser
 * - Si un refresh token est volé, on peut le révoquer en base
 * - Le refresh token permet d'obtenir un NOUVEAU access token sans reconnexion
 *
 * CONCEPT - Rotation du Refresh Token :
 * =====================================
 * À chaque utilisation d'un refresh token, on le RÉVOQUE et on en crée un NOUVEAU.
 * Avantage : si un token volé est utilisé APRÈS que le légitime l'a déjà utilisé,
 * on détecte le vol (le token est déjà révoqué) et on peut invalider TOUTE la session.
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
     * On utilise le même PasswordEncoder que pour les mots de passe (BCrypt).
     *
     * Pourquoi hasher le refresh token ?
     * Même principe que les mots de passe : si la base de données est compromise,
     * les tokens hashés ne sont pas utilisables directement.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Crée un nouveau refresh token pour un utilisateur.
     *
     * PROCESSUS :
     * 1. Générer un UUID aléatoire (le token brut)
     * 2. Hasher ce UUID avec BCrypt
     * 3. Stocker le HASH en base (jamais le token brut !)
     * 4. Retourner le token BRUT au client (il le stocke dans un cookie HttpOnly)
     *
     * @param userId l'ID de l'utilisateur propriétaire du token
     * @return le token brut (UUID) à retourner au client
     * @throws RuntimeException si l'utilisateur n'existe pas
     */
    @Transactional
    public String createRefreshToken(String userId) {
        log.info("Création d'un nouveau refresh token pour l'utilisateur: {}", userId);

        // Récupérer l'utilisateur depuis la base
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));

        // Générer un UUID aléatoire comme token brut
        String rawToken = UUID.randomUUID().toString();

        // Hasher le token AVANT de le stocker en base
        String hashedToken = passwordEncoder.encode(rawToken);

        // Construire l'entité avec le Builder (pattern Lombok)
        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(hashedToken)            // On stocke le HASH, pas le brut
                .user(user)                     // Lien vers l'utilisateur
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRATION_DAYS)) // Expire dans 7 jours
                .revoked(false)                 // Token actif à la création
                .build();

        // Sauvegarder en base de données
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token créé avec succès, expire le: {}", refreshToken.getExpiresAt());

        // Retourner le TOKEN BRUT au client (jamais le hash !)
        return rawToken;
    }

    /**
     * Vérifie la validité d'un refresh token.
     *
     * VÉRIFICATIONS EFFECTUÉES :
     * 1. Le token existe en base
     * 2. Le token n'est pas expiré
     * 3. Le token n'est pas révoqué
     *
     * @param rawToken le token brut envoyé par le client
     * @return l'entité du token si valide, Optional.empty() sinon
     */
    public Optional<RefreshTokenEntity> verifyRefreshToken(String rawToken) {
        log.debug("Vérification du refresh token...");

        // Parcourir tous les tokens actifs de l'utilisateur
        // (on ne peut pas faire de SELECT par hash BCrypt directement)
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository.findAllActiveTokens();

        for (RefreshTokenEntity storedToken : activeTokens) {
            // BCrypt.matches vérifie si le token brut correspond au hash stocké
            if (passwordEncoder.matches(rawToken, storedToken.getToken())) {
                // Token trouvé ! Vérifier qu'il n'est pas expiré ni révoqué
                if (storedToken.isExpired()) {
                    log.warn("Refresh token expiré, id: {}", storedToken.getId());
                    return Optional.empty();
                }
                if (storedToken.isRevoked()) {
                    log.warn("Tentative d'utilisation d'un refresh token révoqué, id: {}", storedToken.getId());
                    return Optional.empty();
                }
                log.debug("Refresh token valide trouvé, id: {}", storedToken.getId());
                return Optional.of(storedToken);
            }
        }

        log.warn("Aucun refresh token correspondant trouvé");
        return Optional.empty();
    }

    /**
     * Révoque un refresh token spécifique.
     *
     * CONCEPT - Rotation :
     * Quand on utilise un refresh token pour obtenir un nouvel access token,
     * on RÉVOQUE l'ancien et on en crée un NOUVEAU.
     * Cela empêche la réutilisation d'un même token plusieurs fois.
     *
     * @param tokenId l'ID du token à révoquer
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
     * Révoque TOUS les refresh tokens d'un utilisateur.
     *
     * UTILISATION : Déconnexion de toutes les sessions.
     * Quand un utilisateur se déconnecte, on révoque TOUS ses tokens
     * pour qu'il ne puisse plus refresh depuis AUCUN appareil.
     *
     * @param userId l'ID de l'utilisateur
     */
    @Transactional
    public void revokeAllUserTokens(String userId) {
        log.info("Révocation de tous les refresh tokens pour l'utilisateur: {}", userId);

        // Récupérer tous les tokens actifs (non révoqués) de l'utilisateur
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository
                .findByUserIdAndRevokedFalse(userId);

        // Marquer chaque token comme révoqué
        for (RefreshTokenEntity token : activeTokens) {
            token.setRevoked(true);
        }

        // Sauvegarder tous les tokens modifiés en une seule transaction
        refreshTokenRepository.saveAll(activeTokens);
        log.info("{} token(s) révoqué(s) pour l'utilisateur: {}",
                activeTokens.size(), userId);
    }
}