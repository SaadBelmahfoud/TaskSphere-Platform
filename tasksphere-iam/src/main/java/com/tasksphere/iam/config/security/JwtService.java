package com.tasksphere.iam.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/*
 * ====================================================================
 * SERVICE JWT (Gestion des tokens d'authentification)
 * ====================================================================
 *
 * PRINCIPE JWT :
 * Un JWT (JSON Web Token) est un token signé cryptographiquement.
 * Il contient 3 parties séparées par des points : Header.Payload.Signature
 * - Header : l'algorithme utilisé (HS256)
 * - Payload : les données (username, rôle, date d'expiration)
 * - Signature : la preuve que le token n'a pas été modifié
 *
 * Le serveur GENÈRE le token avec sa clé secrète.
 * Le serveur VÉRIFIE le token avec la même clé secrète.
 * Personne ne peut modifier le token sans invalider la signature.
 *
 * PRINCIPE @Value :
 * Spring injecte automatiquement la valeur de la propriété YAML dans le champ.
 * Ex: jwt.secret=maclé → secret = "maclé"
 * Cela permet de changer la clé sans recompiler le code.
 *
 * ====================================================================
 * CORRECTION B1 — SECRET CONFIGURABLE VIA VARIABLE D'ENVIRONNEMENT
 * ====================================================================
 *
 * PROBLÈME AVANT :
 *   Jwts.SIG.HS256.key().build() génère une NOUVELLE clé aléatoire à chaque appel.
 *   Conséquence : à chaque redémarrage du serveur, tous les tokens existants
 *   deviennent invalides car la clé de signature a changé !
 *
 * SOLUTION APRÈS :
 *   - Si jwt.secret est configuré (YAML ou variable d'env JWT_SECRET) :
 *     On utilise Keys.hmacShaKeyFor() pour convertir la chaîne en SecretKey.
 *     → La même clé est utilisée à chaque démarrage = tokens valides entre restarts.
 *   - Sinon (mode dev sans config) :
 *     On génère une clé aléatoire. Attention : les tokens seront invalidés au restart.
 *
 * PRINCIPE Keys.hmacShaKeyFor() :
 *   Convertit un tableau de bytes en objet SecretKey compatible HMAC-SHA256.
 *   La clé doit faire au minimum 256 bits (32 bytes) pour HS256.
 *   Si la clé est plus courte, une exception sera levée à la construction.
 */
@Service
public class JwtService {

    /*
     * SECRET KEY :
     * Si une clé est définie dans application.yaml ou variable d'env JWT_SECRET,
     * on la convertit en SecretKey pour qu'elle soit stable entre les redémarrages.
     * Sinon, on génère une clé aléatoire (pour le dev uniquement).
     *
     * EN PRODUCTION : la clé DOIT être définie via variable d'environnement,
     * JAMAIS codée en dur dans le code source ou le YAML.
     */
    private final SecretKey secretKey;

    // Durée de vie de l'access token : 1 heure (3600 secondes)
    private static final long ACCESS_TOKEN_EXPIRATION = 1000 * 60 * 60;

    /**
     * Constructeur : initialise la clé secrète JWT.
     *
     * PRINCIPE @Value("${jwt.secret:}") :
     * - Lit la propriété "jwt.secret" depuis application.yaml
     * - Si la propriété n'existe pas, la valeur par défaut est "" (chaîne vide)
     * - Les variables d'environnement (ex: JWT_SECRET) surchargent les valeurs YAML
     *
     * @param secretFromConfig  La clé secrète depuis la config (YAML ou variable d'env)
     */
    public JwtService(@Value("${jwt.secret:}") String secretFromConfig) {
        if (secretFromConfig != null && !secretFromConfig.isBlank()) {
            /*
             * CORRECTION B1 : On utilise la clé configurée, PAS une clé aléatoire !
             *
             * Keys.hmacShaKeyFor(bytes) crée un SecretKey à partir d'un tableau de bytes.
             * StandardCharsets.UTF_8 garantit un encodage cohérent.
             *
             * ATTENTION : la clé doit faire au minimum 32 caractères (256 bits) pour HS256.
             * Si elle est plus courte, Keys.hmacShaKeyFor() lèvera une WeakKeyException.
             */
            this.secretKey = Keys.hmacShaKeyFor(
                    secretFromConfig.getBytes(StandardCharsets.UTF_8)
            );
        } else {
            /*
             * MODE DEV : Clé aléatoire (pas de config trouvée).
             * ⚠️ Les tokens seront invalidés à chaque redémarrage !
             * Ce code ne doit JAMAIS être utilisé en production.
             */
            this.secretKey = Jwts.SIG.HS256.key().build();
        }
    }

    /**
     * Génère un access token JWT contenant le username et le rôle.
     * Durée de vie : 1 heure.
     *
     * PRINCIPE DU BUILDER :
     * Jwts.builder() utilise le pattern Builder pour construire le token étape par étape.
     * - claims() : ajoute les données personnalisées (rôle)
     * - subject() : identifie le sujet (username)
     * - issuedAt() : date de création
     * - expiration() : date d'expiration (maintenant + 1h)
     * - signWith() : signe avec la clé secrète
     * - compact() : assemble les 3 parties en une chaîne Base64
     */
    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role); // On inclut le rôle dans le token

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Génère un refresh token (opaque string aléatoire, pas un JWT).
     * Ce token sera hashé et stocké en BDD.
     * Le client ne reçoit que le token en clair.
     *
     * PRINCIPE : Un refresh token est opaque (pas de données lisibles à l'intérieur).
     * Il sert uniquement à demander un nouvel access token quand celui-ci expire.
     * Il est stocké en BDD pour pouvoir le révoquer.
     */
    public String generateRefreshToken() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Extrait le username (subject) du token JWT.
     * Le subject est le champ "sub" dans le payload.
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extrait le rôle depuis les claims du token JWT.
     * Le rôle est stocké dans le champ personnalisé "role" du payload.
     */
    public String extractRole(String token) {
        return (String) getClaims(token).get("role");
    }

    /**
     * Vérifie si le token est valide (signature OK + username correspond + non expiré).
     *
     * PRINCIPE DE DOUBLE VÉRIFICATION :
     * 1. Le username extrait doit correspondre au username fourni
     * 2. Le token ne doit pas être expiré
     * Si l'une des deux conditions échoue, le token est invalide.
     */
    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * Vérifie si le token est expiré.
     * Compare la date d'expiration (claim "exp") avec la date actuelle.
     */
    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /**
     * Parse le token JWT et retourne ses claims (données utiles).
     *
     * PRINCIPE DU PARSER :
     * Jwts.parser() analyse le token en 3 étapes :
     * 1. verifyWith(secretKey) : vérifie la signature avec la clé secrète
     * 2. build() : construit le parser
     * 3. parseSignedClaims(token) : extrait le payload signé
     * 4. getPayload() : retourne les claims (Map<String, Object>)
     *
     * Si la signature est invalide ou le token expiré, une exception est levée.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}