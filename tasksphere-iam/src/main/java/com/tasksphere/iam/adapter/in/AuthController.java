package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.config.security.JwtService;
import com.tasksphere.iam.config.security.RefreshTokenService;
import com.tasksphere.iam.domain.RefreshTokenEntity;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.dto.RegisterRequest;
import com.tasksphere.iam.port.out.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : AuthController (Authentification & Inscription)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ENDPOINTS (tous publics — permitAll dans SecurityConfig) :
 * ──────────────────────────────────────────────────────
 * POST /api/v1/auth/login     → Connexion → JWT + Refresh Token
 * POST /api/v1/auth/register  → Inscription → Création USER + JWT
 * POST /api/v1/auth/refresh   → Renouvellement du JWT via Refresh Token
 * POST /api/v1/auth/logout    → Révocation de tous les tokens
 *
 * INSCRIPTION OUVERTE (décision métier) :
 * ─────────────────────────────────────────
 * - N'importe qui peut créer un compte
 * - Rôle par défaut : USER
 * - Pas de vérification email (pour l'instant)
 * - confirmPassword validé côté serveur
 * - Email et username doivent être uniques
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
     * POST /api/v1/auth/login — Connexion.
     *
     * FLUX :
     * 1. Chercher l'utilisateur par email
     * 2. Vérifier le mot de passe avec BCrypt (passwordEncoder.matches)
     * 3. Vérifier que le compte est activé
     * 4. Générer un JWT (accessToken) + Refresh Token
     * 5. Retourner les deux tokens
     *
     * SÉCURITÉ :
     * - On ne révèle PAS si l'email existe ou pas (même message d'erreur)
     *   → Protection contre l'énumération d'utilisateurs
     * - Le mot de passe n'est jamais retourné au client
     * - Le JWT expire après 1h, le refresh token après 7 jours
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String rawPassword = loginRequest.get("password");
        log.info("Tentative de connexion pour: {}", email);

        Optional<UserEntity> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty() || !passwordEncoder.matches(rawPassword, userOpt.get().getPassword())) {
            log.warn("Échec d'authentification pour: {}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Email ou mot de passe incorrect"));
        }

        UserEntity user = userOpt.get();

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("Compte désactivé: {}", email);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Compte désactivé"));
        }

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
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
     * POST /api/v1/auth/register — Inscription.
     *
     * FLUX :
     * 1. Vérifier que password === confirmPassword
     * 2. Vérifier l'unicité de l'email et du username
     * 3. Hacher le mot de passe avec BCrypt
     * 4. Créer l'utilisateur avec le rôle USER par défaut
     * 5. Générer les tokens JWT
     * 6. Retourner les tokens + infos utilisateur
     *
     * @Valid : Active la validation Jakarta sur RegisterRequest :
     * - @NotBlank sur username, firstName, lastName, email, password, confirmPassword
     * - @Email sur email
     * - @Size(min=3, max=50) sur username
     * - @Size(min=6, max=100) sur password
     *
     * VALIDATION CONFIRM PASSWORD :
     * La validation @NotBlank est gérée par Jakarta.
     * La vérification password === confirmPassword est faite
     * explicitement ici (pas de validation Jakarta standard pour ça).
     * Le frontend utilise aussi Zod avec .refine() pour cette vérification.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Tentative d'inscription pour: {}", request.email());

        // Vérification confirmPassword
        if (!request.password().equals(request.confirmPassword())) {
            log.warn("Inscription échouée: mots de passe différents pour {}", request.email());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Les mots de passe ne correspondent pas"));
        }

        // Vérification unicité email et username
        boolean emailExists = userRepository.existsByEmail(request.email());
        boolean usernameExists = userRepository.existsByUsername(request.username());

        if (emailExists || usernameExists) {
            log.warn("Inscription échouée: email ou username déjà pris (email={}, username={})",
                    request.email(), request.username());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Un compte avec cet email ou ce nom d'utilisateur existe déjà"));
        }

        // Création de l'utilisateur avec rôle USER par défaut
        UserEntity newUser = new UserEntity();
        newUser.setUsername(request.username());
        newUser.setFirstName(request.firstName());
        newUser.setLastName(request.lastName());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole("USER");    // ← Rôle par défaut configurable
        newUser.setEnabled(true);   // ← Compte activé immédiatement (pas de vérif email)

        userRepository.save(newUser);
        log.info("Utilisateur créé avec succès: {} (email: {}, rôle: USER)",
                request.username(), request.email());

        // Auto-login : générer les tokens directement après inscription
        String accessToken = jwtService.generateAccessToken(newUser.getEmail(), newUser.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(newUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Inscription réussie",
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer",
                "expiresIn", "3600",
                "user", Map.of(
                        "username", newUser.getUsername(),
                        "email", newUser.getEmail(),
                        "role", newUser.getRole()
                )
        ));
    }

    /**
     * POST /api/v1/auth/refresh — Renouvellement du JWT.
     *
     * PATTERN : Refresh Token Rotation
     * ──────────────────────────────────
     * 1. Le client envoie le refresh token
     * 2. On vérifie qu'il est valide et non expiré
     * 3. On RÉVOQUE l'ancien refresh token (rotation)
     * 4. On crée un NOUVEAU refresh token
     * 5. On génère un nouveau JWT
     * 6. On retourne les deux nouveaux tokens
     *
     * POURQUOI LA ROTATION ?
     * → Si un refresh token est volé, il ne peut être utilisé qu'une fois.
     * → Le légitime propriétaire se rendra compte que son token ne fonctionne
     *   plus → il devra se reconnecter → l'attaquant est éjecté.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> request) {
        String rawRefreshToken = request.get("refreshToken");
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token manquant"));
        }
        log.info("Tentative de refresh token");

        Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);
        if (tokenOpt.isEmpty()) {
            log.warn("Refresh token invalide ou expiré");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token invalide ou expiré"));
        }

        RefreshTokenEntity storedToken = tokenOpt.get();
        UserEntity user = storedToken.getUser();

        // ROTATION : révoquer l'ancien et en créer un nouveau
        refreshTokenService.revokeToken(storedToken.getId());
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        log.info("Refresh token réussi pour: {}", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken,
                "tokenType", "Bearer",
                "expiresIn", "3600"
        ));
    }

    /**
     * POST /api/v1/auth/logout — Déconnexion.
     *
     * Révoque TOUS les refresh tokens de l'utilisateur.
     * Le JWT restant expirera naturellement (1h max).
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody Map<String, String> request) {
        String rawRefreshToken = request.get("refreshToken");
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token manquant"));
        }
        log.info("Tentative de déconnexion");

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