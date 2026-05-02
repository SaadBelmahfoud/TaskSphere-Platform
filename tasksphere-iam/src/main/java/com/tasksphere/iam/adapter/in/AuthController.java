package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.config.security.CookieHelper;
import com.tasksphere.iam.config.security.JwtService;
import com.tasksphere.iam.config.security.RefreshTokenService;
import com.tasksphere.iam.domain.RefreshTokenEntity;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.dto.*;
import com.tasksphere.iam.port.out.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
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
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 1 — CORRECTION P1-5 + P1-6 : HttpOnly Cookies + DTOs
 * ═══════════════════════════════════════════════════════════════════
 *
 * P1-5 — HttpOnly Cookies :
 *   Les tokens sont désormais envoyés DANS LES DEUX formats :
 *   1. Dans le body JSON (rétrocompatibilité mobile/API)
 *   2. En cookies HttpOnly (sécurité navigateur, anti-XSS)
 *
 *   POURQUOI LES DEUX ?
 *   - Cookie HttpOnly → Protection XSS (JS ne peut pas lire le token)
 *   - Body JSON → Rétrocompatibilité avec les clients mobiles/API
 *     qui ne gèrent pas les cookies (curl, Postman, apps mobiles)
 *   - Le frontend navigateur utilisera les cookies en priorité
 *   - Le frontend mobile utilisera le body JSON
 *
 *   FLUX NAVIGATEUR :
 *   ┌───────────────────────────────────────────────────────┐
 *   │ 1. POST /auth/login                                  │
 *   │    → Serveur: Set-Cookie: accessToken=...; HttpOnly   │
 *   │    → Serveur: Set-Cookie: refreshToken=...; HttpOnly  │
 *   │    → Body: { accessToken, refreshToken }             │
 *   │                                                       │
 *   │ 2. GET /api/v1/tasks                                 │
 *   │    → Navigateur envoie automatiquement le cookie      │
 *   │    → JwtAuthenticationFilter lit le cookie            │
 *   │    → Pas besoin de header Authorization manuel        │
 *   │                                                       │
 *   │ 3. POST /auth/refresh                                │
 *   │    → Body VIDE ou avec refreshToken                  │
 *   │    → Si body vide : fallback sur cookie refreshToken │
 *   │    → Cookie mis à jour avec les nouveaux tokens      │
 *   │                                                       │
 *   │ 4. POST /auth/logout                                 │
 *   │    → Body VIDE ou avec refreshToken                  │
 *   │    → Si body vide : fallback sur cookie refreshToken │
 *   │    → Cookies effacés (Max-Age=0)                     │
 *   └───────────────────────────────────────────────────────┘
 *
 * P1-6 — DTOs typés + @Valid :
 *   AVANT : Map<String, String> pour login, refresh, logout
 *   → Aucune validation, typage faible, pas de documentation
 *
 *   APRÈS : DTOs typés (LoginRequest, RefreshTokenRequest, LogoutRequest)
 *   → @Valid active la validation Jakarta automatique
 *   → 400 Bad Request avec détails si validation échoue
 *   → Documentation Swagger auto-générée
 * ═══════════════════════════════════════════════════════════════════
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
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 : Injection de CookieHelper
     * ═══════════════════════════════════════════════════════════════════
     * CookieHelper gère les opérations Set-Cookie / Clear-Cookie.
     * Il est injecté via le constructeur (@RequiredArgsConstructor).
     *
     * DOUBLE CANAL DE TOKENS :
     * ──────────────────────────
     * 1. Cookies HttpOnly → Sécurité navigateur (anti-XSS)
     * 2. Body JSON → Rétrocompatibilité API/mobile
     *
     * Le navigateur envoie automatiquement les cookies avec chaque
     * requête correspondant au Path. Le client mobile/API doit
     * gérer manuellement le stockage des tokens du body JSON.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final CookieHelper cookieHelper;

    /**
     * POST /api/v1/auth/login — Connexion.
     *
     * FLUX :
     * 1. Chercher l'utilisateur par email
     * 2. Vérifier le mot de passe avec BCrypt (passwordEncoder.matches)
     * 3. Vérifier que le compte est activé
     * 4. Générer un JWT (accessToken) + Refresh Token
     * 5. ═══════════════════════════════════════════════════════
     *    P1-5 : Envoyer les tokens en cookies HttpOnly
     *    ═══════════════════════════════════════════════════════
     * 6. Retourner les deux tokens dans le body (rétrocompatibilité)
     *
     * SÉCURITÉ :
     * - On ne révèle PAS si l'email existe ou pas (même message d'erreur)
     *   → Protection contre l'énumération d'utilisateurs
     * - Le mot de passe n'est jamais retourné au client
     * - Le JWT expire après 1h, le refresh token après 7 jours
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-6 : LoginRequest DTO + @Valid
     * ═══════════════════════════════════════════════════════════════════
     * AVANT : @RequestBody Map<String, String> loginRequest
     * APRÈS : @Valid @RequestBody LoginRequest loginRequest
     * → @Email vérifie le format, @NotBlank empêche les champs vides
     * ═══════════════════════════════════════════════════════════════════
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {
        String email = loginRequest.email();
        String rawPassword = loginRequest.password();
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

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Émettre les cookies HttpOnly
        // ═══════════════════════════════════════════════════════
        // CookieHelper ajoute les headers Set-Cookie à la réponse.
        // Le navigateur stocke les cookies automatiquement.
        // - accessToken : Path=/ → envoyé sur TOUTES les requêtes
        // - refreshToken : Path=/api/v1/auth → envoyé SEULEMENT sur /auth/**
        //
        // Les tokens sont TOUJOURS aussi dans le body JSON pour
        // la rétrocompatibilité avec les clients non-navigateur.
        // ═══════════════════════════════════════════════════════
        cookieHelper.setAuthCookies(response, accessToken, refreshToken);

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
     * 6. ═══════════════════════════════════════════════════════
     *    P1-5 : Envoyer les tokens en cookies HttpOnly
     *    ═══════════════════════════════════════════════════════
     * 7. Retourner les tokens + infos utilisateur
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
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
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

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Émettre les cookies HttpOnly après inscription
        // ═══════════════════════════════════════════════════════
        cookieHelper.setAuthCookies(response, accessToken, refreshToken);

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
     * 1. Le client envoie le refresh token (body OU cookie)
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
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 + P1-6 : HttpOnly Cookies + RefreshTokenRequest DTO
     * ═══════════════════════════════════════════════════════════════════
     * Le refresh token peut provenir de DEUX sources :
     * 1. Le body de la requête (RefreshTokenRequest DTO) — clients API/mobile
     * 2. Le cookie HttpOnly "refreshToken" — navigateur (cookie-only mode)
     *
     * PRIO : body > cookie (si les deux existent, le body gagne)
     *
     * Si le body est absent/vide, on lit le cookie comme fallback.
     * Cela permet au frontend navigateur de fonctionner SANS stocker
     * le refreshToken en localStorage → protection XSS améliorée.
     *
     * Après le refresh, on met à jour les cookies avec les nouveaux tokens.
     * C'est crucial car l'ancien refresh token est révoqué (rotation).
     * ═══════════════════════════════════════════════════════════════════
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Double source pour le refresh token
        // ═══════════════════════════════════════════════════════
        // CHEMIN 1 : Body de la requête (clients API/mobile)
        // CHEMIN 2 : Cookie HttpOnly "refreshToken" (navigateur)
        //
        // Le body est (required = false) pour permettre le mode
        // cookie-only. Si le body est null ou le token est vide,
        // on fallback sur le cookie.
        // ═══════════════════════════════════════════════════════
        String rawRefreshToken = null;
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            rawRefreshToken = request.refreshToken();
            log.info("Tentative de refresh token via body");
        } else {
            // Fallback : lire le refresh token depuis le cookie
            rawRefreshToken = extractCookieValue(httpRequest, "refreshToken");
            if (rawRefreshToken != null) {
                log.info("Tentative de refresh token via cookie HttpOnly");
            }
        }

        if (rawRefreshToken == null) {
            log.warn("Refresh token absent (ni body ni cookie)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Refresh token requis (body ou cookie)"));
        }

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

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Mettre à jour les cookies après rotation
        // ═══════════════════════════════════════════════════════
        // L'ancien refresh token est révoqué, on doit mettre à jour
        // le cookie avec le nouveau token. Sinon, la prochaine
        // tentative de refresh échouera avec un token révoqué.
        // ═══════════════════════════════════════════════════════
        cookieHelper.setAuthCookies(response, newAccessToken, newRefreshToken);

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
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 + P1-6 : Effacer les cookies + LogoutRequest DTO
     * ═══════════════════════════════════════════════════════════════════
     * La déconnexion DOIT effacer les cookies HttpOnly.
     * Sinon, le navigateur continue d'envoyer les cookies →
     * l'utilisateur reste "connecté" malgré le logout.
     *
     * Le refresh token peut provenir du body OU du cookie (comme refresh).
     * Si aucun token n'est trouvé, on efface quand même les cookies
     * par sécurité (cookies périmés/corrompus).
     *
     * PRINCIPE DE SUPPRESSION DE COOKIE :
     * On recrée le cookie avec Max-Age=0 → le navigateur le supprime.
     * Il faut les MÊMES propriétés (Path, Domain) que lors de la création.
     * ═══════════════════════════════════════════════════════════════════
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Double source pour le refresh token
        // ═══════════════════════════════════════════════════════
        // Même logique que refresh() : body en priorité, cookie en fallback.
        // Cela permet au frontend navigateur de se déconnecter sans
        // avoir besoin du refreshToken dans le body.
        // ═══════════════════════════════════════════════════════
        String rawRefreshToken = null;
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            rawRefreshToken = request.refreshToken();
            log.info("Tentative de déconnexion via body");
        } else {
            rawRefreshToken = extractCookieValue(httpRequest, "refreshToken");
            if (rawRefreshToken != null) {
                log.info("Tentative de déconnexion via cookie HttpOnly");
            }
        }

        if (rawRefreshToken != null) {
            Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);
            if (tokenOpt.isPresent()) {
                RefreshTokenEntity storedToken = tokenOpt.get();
                refreshTokenService.revokeAllUserTokens(storedToken.getUser().getId());
                log.info("Déconnexion réussie pour: {}", storedToken.getUser().getEmail());
            } else {
                log.warn("Tentative de déconnexion avec un token invalide");
            }
        } else {
            log.warn("Déconnexion sans token — cookies effacés par sécurité");
        }

        // ═══════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Effacer les cookies HttpOnly
        // ═══════════════════════════════════════════════════════
        // TOUJOURS effacer les cookies, même si le token est invalide.
        // Par sécurité : si le client a des cookies périmés ou
        // corrompus, on veut les nettoyer de toute façon.
        // ═══════════════════════════════════════════════════════
        cookieHelper.clearAuthCookies(response);

        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 : Extraction d'un cookie par nom
     * ═══════════════════════════════════════════════════════════════════
     * Utilitaire pour lire la valeur d'un cookie HttpOnly depuis
     * la requête HTTP. Le code serveur Java PEUT lire les cookies
     * HttpOnly (seul JavaScript ne le peut pas).
     *
     * @param request   La requête HTTP contenant les cookies
     * @param cookieName Le nom du cookie à chercher
     * @return La valeur du cookie, ou null si absent
     * ═══════════════════════════════════════════════════════════════════
     */
    private String extractCookieValue(jakarta.servlet.http.HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}