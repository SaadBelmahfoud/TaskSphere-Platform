package com.tasksphere.iam.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
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
 */
@Service
public class JwtService {

    /*
     * SECRET KEY :
     * Si une clé est définie dans application.yaml, on l'utilise.
     * Sinon, on génère une clé aléatoire (pour le dev uniquement).
     * En production, la clé DOIT être définie dans les variables d'environnement.
     */
    private final SecretKey secretKey;

    // Durée de vie de l'access token : 1 heure (3600 secondes)
    private static final long ACCESS_TOKEN_EXPIRATION = 1000 * 60 * 60;

    public JwtService(@Value("${jwt.secret:}") String secretFromConfig) {
        if (secretFromConfig != null && !secretFromConfig.isBlank()) {
            // Si une clé est configurée, on la convertit en SecretKey
            this.secretKey = Jwts.SIG.HS256.key().build();
        } else {
            // Sinon, clé aléatoire (dev mode)
            this.secretKey = Jwts.SIG.HS256.key().build();
        }
    }

    /**
     * Génère un access token JWT contenant le username et le rôle.
     * Durée de vie : 1 heure.
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
     */
    public String generateRefreshToken() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Extrait le username (subject) du token JWT.
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extrait le rôle depuis les claims du token JWT.
     */
    public String extractRole(String token) {
        return (String) getClaims(token).get("role");
    }

    /**
     * Vérifie si le token est valide (signature OK + username correspond + non expiré).
     */
    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * Vérifie si le token est expiré.
     */
    public boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /**
     * Parse le token JWT et retourne ses claims (données utiles).
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}