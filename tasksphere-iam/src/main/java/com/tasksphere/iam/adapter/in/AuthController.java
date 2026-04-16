package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.config.security.JwtService;
import com.tasksphere.iam.config.security.RefreshTokenService;
import com.tasksphere.iam.domain.RefreshTokenEntity;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contrôleur REST gérant l'authentification.
 *
 * CONCEPT - Adapter d'entrée (Driving Adapter) en architecture hexagonale :
 * ======================================================================
 * Ce contrôleur traduit les requêtes HTTP en appels au domaine.
 * Il ne contient AUCUNE logique métier, uniquement :
 * - Extraction des paramètres de la requête
 * - Appels aux services
 * - Construction de la réponse HTTP
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * POST /api/v1/auth/login
     * Authentifie un utilisateur et retourne les tokens.
     *
     * PROCESSUS :
     * 1. Chercher l'utilisateur par email
     * 2. Vérifier le mot de passe avec BCrypt (passwordEncoder.matches)
     * 3. Générer un access token JWT (1h, stateless)
     * 4. Générer un refresh token opaque (7j, stocké hashé en base)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String rawPassword = loginRequest.get("password");

        log.info("Tentative de connexion pour: {}", email);

        // Étape 1 : Chercher l'utilisateur par EMAIL
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);

        // Étape 2 : Vérifier que l'utilisateur existe ET que le mot de passe est correct
        if (userOpt.isEmpty() || !passwordEncoder.matches(rawPassword, userOpt.get().getPassword())) {
            log.warn("Échec d'authentification pour: {}", email);
            // Message GÉNÉRIQUE pour éviter l'énumération des comptes
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Email ou mot de passe incorrect"));
        }

        UserEntity user = userOpt.get();

        // Vérifier que le compte est actif
        // NOTE : enabled est de type Boolean (wrapper), Lombok génère getEnabled()
        // Si c'était un boolean (primitif), Lombok générerait isEnabled()
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("Compte désactivé: {}", email);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Compte désactivé"));
        }

        // Étape 3 : Générer l'access token JWT
        // role est un String ("USER", "MANAGER", "ADMIN"), pas un enum
        // donc pas besoin de .name()
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole()
        );

        // Étape 4 : Générer le refresh token (stocké hashé en base)
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("Connexion réussie pour: {}", email);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer",
                "expiresIn", "3600"
        ));
    }

    /**
     * POST /api/v1/auth/refresh
     * Renouvelle l'access token via le refresh token avec ROTATION.
     *
     * ROTATION = on révoque l'ancien token et on en crée un nouveau.
     * Si un token volé est réutilisé après rotation, il sera rejeté.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> request) {
        String rawRefreshToken = request.get("refreshToken");

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token manquant"));
        }

        log.info("Tentative de refresh token");

        // Vérifier la validité du refresh token
        Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);

        if (tokenOpt.isEmpty()) {
            log.warn("Refresh token invalide ou expiré");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token invalide ou expiré"));
        }

        RefreshTokenEntity storedToken = tokenOpt.get();
        UserEntity user = storedToken.getUser();

        // ROTATION : révoquer l'ancien token et en créer un nouveau
        refreshTokenService.revokeToken(storedToken.getId());
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Générer un nouveau access token (role est un String, pas un enum)
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole()
        );

        log.info("Refresh token réussi pour: {}", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken,
                "tokenType", "Bearer",
                "expiresIn", "3600"
        ));
    }

    /**
     * POST /api/v1/auth/logout
     * Déconnecte l'utilisateur en révoquant TOUS ses refresh tokens.
     * L'access token expirera naturellement après 1h.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody Map<String, String> request) {
        String rawRefreshToken = request.get("refreshToken");

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token manquant"));
        }

        log.info("Tentative de déconnexion");

        // Trouver le token et révoquer TOUS les tokens de l'utilisateur
        Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);

        if (tokenOpt.isPresent()) {
            RefreshTokenEntity storedToken = tokenOpt.get();
            refreshTokenService.revokeAllUserTokens(storedToken.getUser().getId());
            log.info("Déconnexion réussie pour: {}", storedToken.getUser().getEmail());
        } else {
            log.warn("Tentative de déconnexion avec un token invalide");
        }

        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}