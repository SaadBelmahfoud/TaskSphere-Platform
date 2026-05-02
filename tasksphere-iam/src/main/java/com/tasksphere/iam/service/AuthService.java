package com.tasksphere.iam.service;

import com.tasksphere.iam.config.security.JwtService;
import com.tasksphere.iam.config.security.RefreshTokenService;
import com.tasksphere.iam.domain.RefreshTokenEntity;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.dto.RegisterRequest;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE D'APPLICATION : AuthService
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 2 — TÂCHE 2 : Extraction de la logique Auth depuis AuthController
 * ─────────────────────────────────────────────────────────────────
 *
 * PROBLÈME AVANT :
 *   AuthController (482 lignes !) était un "fat controller" contenant :
 *   - Vérification du mot de passe (passwordEncoder.matches)
 *   - Vérification du compte activé (user.getEnabled)
 *   - Vérification de l'unicité email/username
 *   - Création de l'utilisateur (new UserEntity + setters)
 *   - Génération des tokens (JWT + Refresh)
 *   - Extraction des cookies (préoccupation HTTP dans le service !)
 *   - Vérification du refresh token
 *   - Rotation du refresh token
 *   - Révocation des tokens
 *
 *   PRINCIPES VIOLÉS :
 *   - SRP : le contrôleur faisait trop de choses
 *   - Séparation des couches : logique métier dans la couche HTTP
 *   - Testabilité : impossible de tester la logique sans MockMvc
 *
 * SOLUTION APRÈS :
 *   AuthService contient TOUTE la logique métier d'authentification :
 *   - login() : vérifie les credentials et génère les tokens
 *   - register() : crée un utilisateur et génère les tokens
 *   - refresh() : vérifie et renouvelle les tokens
 *   - logout() : révoque les tokens
 *
 *   AuthController ne fait PLUS que :
 *   1. Recevoir la requête HTTP
 *   2. Appeler le service
 *   3. Gérer les cookies (Set-Cookie / Clear-Cookie) — préoccupation HTTP
 *   4. Retourner la réponse HTTP
 *
 * PRINCIPE THIN CONTROLLER / FAT SERVICE :
 *   (Même principe que la Tâche 1 — voir DashboardController)
 *
 * EXCEPTIONS VS RESPONSE ENTITY :
 * ────────────────────────────────
 * APPROCHE CHOISIE : ResponseStatusException
 * Le service lève des ResponseStatusException pour les erreurs.
 * Le contrôleur n'a PAS besoin de if/else pour les cas d'erreur.
 * Spring Boot les attrape automatiquement et retourne le bon code HTTP.
 *
 * POURQUOI PAS UN DOMAIN EXCEPTION CUSTOM ?
 * On pourrait créer des exceptions métier (InvalidCredentialsException,
 * AccountDisabledException, etc.) et les mapper dans un @ExceptionHandler.
 * C'est plus propre mais hors périmètre de cette tâche.
 * On garde ResponseStatusException pour la simplicité.
 *
 * RECORDS DE RÉSULTAT :
 * ─────────────────────
 * Les méthodes retournent des records (AuthResult, RegisterResult)
 * qui encapsulent les tokens + infos utilisateur. Le contrôleur
 * les utilise pour construire la réponse HTTP (body + cookies).
 * Cela permet de TESTER le service sans dépendance HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * RECORD DE RÉSULTAT : AuthResult
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE : Encapsuler le résultat de l'authentification dans un record.
     * Le contrôleur utilise ce record pour :
     * - Construire le body JSON (accessToken, refreshToken, tokenType, expiresIn)
     * - Définir les cookies HttpOnly (via CookieHelper)
     *
     * POURQUOI UN RECORD ET PAS UN MAP ?
     * - Typage fort : chaque champ a un type explicite
     * - Immutabilité : les valeurs ne changent pas après construction
     * - Documentation : les noms des champs sont auto-documentés
     * - IDE : auto-complétion et refactoring facilités
     */
    public record AuthResult(String accessToken, String refreshToken) {}

    /**
     * RECORD DE RÉSULTAT : RegisterResult
     *
     * Étend AuthResult avec les infos utilisateur (pour le body de /register).
     * Le frontend affiche le nom et le rôle après inscription.
     */
    public record RegisterResult(String accessToken, String refreshToken,
                                 String username, String email, String role) {}

    /**
     * ═══════════════════════════════════════════════════════════════════
     * LOGIN : Vérifie les credentials et génère les tokens
     * ═══════════════════════════════════════════════════════════════════
     *
     * FLUX :
     * 1. Chercher l'utilisateur par email
     * 2. Vérifier le mot de passe avec BCrypt (passwordEncoder.matches)
     * 3. Vérifier que le compte est activé
     * 4. Générer un JWT (accessToken) + Refresh Token
     * 5. Retourner AuthResult (tokens)
     *
     * SÉCURITÉ :
     * - On ne révèle PAS si l'email existe ou pas (même message d'erreur)
     *   → Protection contre l'énumération d'utilisateurs
     * - Le mot de passe n'est jamais retourné au client
     * - Le JWT expire après 1h, le refresh token après 7 jours
     *
     * @param email       L'email de l'utilisateur
     * @param rawPassword Le mot de passe en clair (envoyé par le client)
     * @return AuthResult contenant les tokens
     * @throws ResponseStatusException 401 si credentials invalides
     * @throws ResponseStatusException 403 si compte désactivé
     */
    @Transactional
    public AuthResult login(String email, String rawPassword) {
        log.info("SERVICE : Tentative de connexion pour: {}", email);

        // 1. Chercher l'utilisateur par email
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);

        // 2. Vérifier le mot de passe
        // NOTE : On combine les deux vérifications (email existe + mdp correct)
        // dans un seul if pour ne PAS révéler si l'email existe ou pas.
        // C'est la protection contre l'énumération d'utilisateurs.
        if (userOpt.isEmpty() || !passwordEncoder.matches(rawPassword, userOpt.get().getPassword())) {
            log.warn("SERVICE : Échec d'authentification pour: {}", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Email ou mot de passe incorrect");
        }

        UserEntity user = userOpt.get();

        // 3. Vérifier que le compte est activé
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("SERVICE : Compte désactivé: {}", email);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Compte désactivé");
        }

        // 4. Générer les tokens
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("SERVICE : Connexion réussie pour: {}", email);
        return new AuthResult(accessToken, refreshToken);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * REGISTER : Crée un utilisateur et génère les tokens
     * ═══════════════════════════════════════════════════════════════════
     *
     * FLUX :
     * 1. Vérifier que password === confirmPassword
     * 2. Vérifier l'unicité de l'email et du username
     * 3. Hacher le mot de passe avec BCrypt
     * 4. Créer l'utilisateur avec le rôle USER par défaut
     * 5. Générer les tokens JWT
     * 6. Retourner RegisterResult (tokens + infos utilisateur)
     *
     * INSCRIPTION OUVERTE (décision métier) :
     * - N'importe qui peut créer un compte
     * - Rôle par défaut : USER
     * - Pas de vérification email (pour l'instant)
     *
     * @param request Le DTO d'inscription (username, firstName, lastName, email, password, confirmPassword)
     * @return RegisterResult contenant les tokens + infos utilisateur
     * @throws ResponseStatusException 400 si mots de passe différents
     * @throws ResponseStatusException 409 si email ou username déjà pris
     */
    @Transactional
    public RegisterResult register(RegisterRequest request) {
        log.info("SERVICE : Tentative d'inscription pour: {}", request.email());

        // 1. Vérification confirmPassword
        if (!request.password().equals(request.confirmPassword())) {
            log.warn("SERVICE : Inscription échouée: mots de passe différents pour {}", request.email());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les mots de passe ne correspondent pas");
        }

        // 2. Vérification unicité email et username
        boolean emailExists = userRepository.existsByEmail(request.email());
        boolean usernameExists = userRepository.existsByUsername(request.username());

        if (emailExists || usernameExists) {
            log.warn("SERVICE : Inscription échouée: email ou username déjà pris (email={}, username={})",
                    request.email(), request.username());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte avec cet email ou ce nom d'utilisateur existe déjà");
        }

        // 3. Création de l'utilisateur avec rôle USER par défaut
        UserEntity newUser = new UserEntity();
        newUser.setUsername(request.username());
        newUser.setFirstName(request.firstName());
        newUser.setLastName(request.lastName());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole("USER");    // ← Rôle par défaut configurable
        newUser.setEnabled(true);   // ← Compte activé immédiatement (pas de vérif email)

        userRepository.save(newUser);
        log.info("SERVICE : Utilisateur créé avec succès: {} (email: {}, rôle: USER)",
                request.username(), request.email());

        // 4. Auto-login : générer les tokens directement après inscription
        String accessToken = jwtService.generateAccessToken(newUser.getEmail(), newUser.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(newUser.getId());

        return new RegisterResult(accessToken, refreshToken,
                newUser.getUsername(), newUser.getEmail(), newUser.getRole());
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * REFRESH : Renouvelle le JWT via le refresh token
     * ═══════════════════════════════════════════════════════════════════
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
     * DOUBLE SOURCE DU REFRESH TOKEN :
     * ─────────────────────────────────
     * Le refresh token peut provenir de DEUX sources :
     * 1. Le body de la requête (clients API/mobile)
     * 2. Le cookie HttpOnly "refreshToken" (navigateur)
     *
     * PRIO : body > cookie (si les deux existent, le body gagne)
     *
     * @param rawRefreshToken Le refresh token brut (peut être null si cookie)
     * @return AuthResult contenant les nouveaux tokens
     * @throws ResponseStatusException 401 si token absent, invalide ou expiré
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        // Vérifier que le token est présent
        if (rawRefreshToken == null) {
            log.warn("SERVICE : Refresh token absent (ni body ni cookie)");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token requis (body ou cookie)");
        }

        // Vérifier la validité du refresh token
        Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);
        if (tokenOpt.isEmpty()) {
            log.warn("SERVICE : Refresh token invalide ou expiré");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token invalide ou expiré");
        }

        RefreshTokenEntity storedToken = tokenOpt.get();
        UserEntity user = storedToken.getUser();

        // ROTATION : révoquer l'ancien et en créer un nouveau
        refreshTokenService.revokeToken(storedToken.getId());
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());

        log.info("SERVICE : Refresh token réussi pour: {}", user.getEmail());
        return new AuthResult(newAccessToken, newRefreshToken);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * LOGOUT : Révoque tous les tokens de l'utilisateur
     * ═══════════════════════════════════════════════════════════════════
     *
     * Révoque TOUS les refresh tokens de l'utilisateur.
     * Le JWT restant expirera naturellement (1h max).
     *
     * Le refresh token peut provenir du body OU du cookie (comme refresh).
     * Si aucun token n'est trouvé, on ne fait rien de spécial car les
     * cookies seront effacés par le contrôleur dans tous les cas.
     *
     * @param rawRefreshToken Le refresh token brut (peut être null)
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null) {
            Optional<RefreshTokenEntity> tokenOpt = refreshTokenService.verifyRefreshToken(rawRefreshToken);
            if (tokenOpt.isPresent()) {
                RefreshTokenEntity storedToken = tokenOpt.get();
                refreshTokenService.revokeAllUserTokens(storedToken.getUser().getId());
                log.info("SERVICE : Déconnexion réussie pour: {}", storedToken.getUser().getEmail());
            } else {
                log.warn("SERVICE : Tentative de déconnexion avec un token invalide");
            }
        } else {
            log.warn("SERVICE : Déconnexion sans token — cookies effacés par le contrôleur");
        }
    }
}