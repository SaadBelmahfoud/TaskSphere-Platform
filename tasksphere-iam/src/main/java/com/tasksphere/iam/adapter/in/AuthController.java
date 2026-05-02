package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.config.security.CookieHelper;
import com.tasksphere.iam.dto.LoginRequest;
import com.tasksphere.iam.dto.LogoutRequest;
import com.tasksphere.iam.dto.RegisterRequest;
import com.tasksphere.iam.dto.RefreshTokenRequest;
import com.tasksphere.iam.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 1 — P1-5 + P1-6 : HttpOnly Cookies + DTOs
 * ═══════════════════════════════════════════════════════════════════
 *
 * P1-5 — HttpOnly Cookies :
 *   Les tokens sont envoyés DANS LES DEUX formats :
 *   1. Dans le body JSON (rétrocompatibilité mobile/API)
 *   2. En cookies HttpOnly (sécurité navigateur, anti-XSS)
 *
 * P1-6 — DTOs typés + @Valid :
 *   AVANT : Map<String, String> pour login, refresh, logout
 *   APRÈS : DTOs typés (LoginRequest, RefreshTokenRequest, LogoutRequest)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 2 — TÂCHE 2 : Thin Controller — Extraction vers AuthService
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (482 lignes !) :
 *   Le contrôleur contenait TOUTE la logique métier :
 *   - Vérification du mot de passe
 *   - Vérification du compte activé
 *   - Création de l'utilisateur
 *   - Génération des tokens
 *   - Vérification du refresh token
 *   - Rotation des tokens
 *   - Révocation des tokens
 *
 * APRÈS (~120 lignes) :
 *   Le contrôleur ne fait PLUS que :
 *   1. Recevoir la requête HTTP
 *   2. Appeler le service (AuthService)
 *   3. Gérer les cookies HttpOnly (préoccupation HTTP)
 *   4. Construire et retourner la réponse HTTP
 *
 *   TOUTE la logique métier est dans AuthService.
 *
 * RESPONSABILITÉS RESTANTES DANS LE CONTRÔLEUR :
 * ──────────────────────────────────────────────
 * - Extraction du refresh token depuis le cookie (fallback)
 *   → C'est une préoccupation HTTP (lecture des cookies de la requête)
 *   → Le service ne doit pas connaître HttpServletRequest
 * - Set-Cookie / Clear-Cookie sur HttpServletResponse
 *   → C'est une préoccupation HTTP (écrire dans la réponse)
 *   → Le service ne doit pas connaître HttpServletResponse
 * - Construction du body JSON de réponse
 *   → C'est une préoccupation HTTP (format de la réponse)
 *   → Le service retourne des records, le contrôleur les convertit en Map
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 2 : Service injecté à la place des dépendances directes
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT : 4 dépendances injectées directement :
     *   private final UserRepository userRepository;
     *   private final PasswordEncoder passwordEncoder;
     *   private final JwtService jwtService;
     *   private final RefreshTokenService refreshTokenService;
     *   private final CookieHelper cookieHelper;
     *
     * APRÈS : 2 dépendances seulement :
     *   private final AuthService authService;
     *   private final CookieHelper cookieHelper;
     *
     * CookieHelper reste dans le contrôleur car la gestion des cookies
     * est une préoccupation HTTP (lecture/écriture des headers Set-Cookie).
     * ═══════════════════════════════════════════════════════════════════
     */
    private final AuthService authService;
    private final CookieHelper cookieHelper;

    /**
     * POST /api/v1/auth/login — Connexion.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 2 : Méthode simplifiée
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (~40 lignes de logique) :
     *   - userRepository.findByEmail()
     *   - passwordEncoder.matches()
     *   - user.getEnabled() check
     *   - jwtService.generateAccessToken()
     *   - refreshTokenService.createRefreshToken()
     *   - cookieHelper.setAuthCookies()
     *
     * APRÈS (~10 lignes) :
     *   - authService.login(email, password) → AuthResult
     *   - cookieHelper.setAuthCookies() (préoccupation HTTP)
     *   - Construction du body JSON
     *
     * GESTION DES ERREURS :
     * Le service lève des ResponseStatusException.
     * Spring Boot les attrape automatiquement et retourne le bon code HTTP.
     * Le contrôleur n'a PAS de try-catch ni de if/else pour les erreurs.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {

        // Appel au service — peut lever ResponseStatusException (401 ou 403)
        AuthService.AuthResult result = authService.login(
                loginRequest.email(), loginRequest.password());

        // Gestion des cookies HttpOnly (préoccupation HTTP)
        cookieHelper.setAuthCookies(response, result.accessToken(), result.refreshToken());

        // Construction du body JSON
        return ResponseEntity.ok(Map.of(
                "accessToken", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "tokenType", "Bearer",
                "expiresIn", "3600"
        ));
    }

    /**
     * POST /api/v1/auth/register — Inscription.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 2 : Méthode simplifiée
     * ═══════════════════════════════════════════════════════════════════
     *
     * AVANT (~50 lignes de logique) :
     *   - Vérification confirmPassword
     *   - existsByEmail / existsByUsername
     *   - new UserEntity() + setters
     *   - passwordEncoder.encode()
     *   - jwtService + refreshTokenService
     *   - cookieHelper.setAuthCookies()
     *
     * APRÈS (~15 lignes) :
     *   - authService.register(request) → RegisterResult
     *   - cookieHelper.setAuthCookies() (préoccupation HTTP)
     *   - Construction du body JSON
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        // Appel au service — peut lever ResponseStatusException (400 ou 409)
        AuthService.RegisterResult result = authService.register(request);

        // Gestion des cookies HttpOnly (préoccupation HTTP)
        cookieHelper.setAuthCookies(response, result.accessToken(), result.refreshToken());

        // Construction du body JSON
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Inscription réussie",
                "accessToken", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "tokenType", "Bearer",
                "expiresIn", "3600",
                "user", Map.of(
                        "username", result.username(),
                        "email", result.email(),
                        "role", result.role()
                )
        ));
    }

    /**
     * POST /api/v1/auth/refresh — Renouvellement du JWT.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 2 : Extraction du cookie + délégation au service
     * ═══════════════════════════════════════════════════════════════════
     *
     * Le contrôleur garde la responsabilité de lire le cookie HttpOnly
     * car c'est une préoccupation HTTP. Le service ne connaît pas
     * HttpServletRequest.
     *
     * PRIO : body > cookie (si les deux existent, le body gagne)
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // ═══════════════════════════════════════════════════════
        // Double source pour le refresh token (préoccupation HTTP)
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
            log.info("CONTROLLER : Refresh token via body");
        } else {
            // Fallback : lire le refresh token depuis le cookie HttpOnly
            rawRefreshToken = extractCookieValue(httpRequest, "refreshToken");
            if (rawRefreshToken != null) {
                log.info("CONTROLLER : Refresh token via cookie HttpOnly");
            }
        }

        // Appel au service — peut lever ResponseStatusException (401)
        AuthService.AuthResult result = authService.refresh(rawRefreshToken);

        // Mise à jour des cookies avec les nouveaux tokens (après rotation)
        cookieHelper.setAuthCookies(response, result.accessToken(), result.refreshToken());

        return ResponseEntity.ok(Map.of(
                "accessToken", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "tokenType", "Bearer",
                "expiresIn", "3600"
        ));
    }

    /**
     * POST /api/v1/auth/logout — Déconnexion.
     *
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 2 — TÂCHE 2 : Extraction du cookie + délégation au service
     * ═══════════════════════════════════════════════════════════════════
     *
     * Le contrôleur garde la responsabilité de :
     * 1. Lire le cookie HttpOnly (préoccupation HTTP)
     * 2. Effacer les cookies (Set-Cookie: Max-Age=0)
     *
     * PRINCIPE DE SUPPRESSION DE COOKIE :
     * On recrée le cookie avec Max-Age=0 → le navigateur le supprime.
     * Il faut les MÊMES propriétés (Path, Domain) que lors de la création.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // Double source pour le refresh token (même logique que refresh)
        String rawRefreshToken = null;
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            rawRefreshToken = request.refreshToken();
            log.info("CONTROLLER : Déconnexion via body");
        } else {
            rawRefreshToken = extractCookieValue(httpRequest, "refreshToken");
            if (rawRefreshToken != null) {
                log.info("CONTROLLER : Déconnexion via cookie HttpOnly");
            }
        }

        // Appel au service — révoque les tokens
        authService.logout(rawRefreshToken);

        // TOUJOURS effacer les cookies, même si le token est invalide.
        // Par sécurité : si le client a des cookies périmés ou corrompus,
        // on veut les nettoyer de toute façon.
        cookieHelper.clearAuthCookies(response);

        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * MÉTHODE UTILITAIRE : Extraction d'un cookie par nom
     * ═══════════════════════════════════════════════════════════════════
     * Cette méthode reste dans le contrôleur car la lecture des cookies
     * est une préoccupation HTTP. Le service ne doit PAS dépendre
     * de HttpServletRequest.
     *
     * Le code serveur Java PEUT lire les cookies HttpOnly (seul
     * JavaScript ne le peut pas).
     *
     * @param request    La requête HTTP contenant les cookies
     * @param cookieName Le nom du cookie à chercher
     * @return La valeur du cookie, ou null si absent
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