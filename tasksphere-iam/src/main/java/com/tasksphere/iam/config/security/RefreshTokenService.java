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
     * Crée un nouveau refresh token pour un utilisateur.
     */
    @Transactional
    public String createRefreshToken(String userId) {
        log.info("Création d'un nouveau refresh token pour l'utilisateur: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = passwordEncoder.encode(rawToken);

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(hashedToken)
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
     */
    public Optional<RefreshTokenEntity> verifyRefreshToken(String rawToken) {
        log.debug("Vérification du refresh token...");

        // Récupérer tous les tokens non révoqués (convention Spring Data : findByRevokedFalse)
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository.findByRevokedFalse();

        for (RefreshTokenEntity storedToken : activeTokens) {
            // BCrypt.matches vérifie si le token brut correspond au hash stocké
            if (passwordEncoder.matches(rawToken, storedToken.getToken())) {
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