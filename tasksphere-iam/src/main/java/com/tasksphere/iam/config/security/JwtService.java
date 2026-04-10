package com.tasksphere.iam.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    /*
     * NOUVEAUTÉ 0.12.x : On n'utilise plus SignatureAlgorithm.
     * On demande directement à Jwts de générer une clé sécurisée pour l'algorithme par défaut (HS256).
     */
    private static final SecretKey SECRET_KEY = Jwts.SIG.HS256.key().build();

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();

        return Jwts.builder()
                .claims(claims)           // NOUVEAU : remplace setClaims()
                .subject(username)         // NOUVEAU : remplace setSubject()
                .issuedAt(new Date())     // NOUVEAU : remplace setIssuedAt()
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // NOUVEAU : remplace setExpiration()
                .signWith(SECRET_KEY)     // PLUS BESOIN de préciser l'algorithme, la clé le contient
                .compact();
    }

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    /*
     * NOUVEAUTÉ 0.12.x : La méthode centrale de parsing.
     * Plus de parserBuilder(). La syntaxe est devenue fluide et plus stricte.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)       // NOUVEAU : remplace setSigningKey()
                .build()                      // Construit le parser
                .parseSignedClaims(token)      // NOUVEAU : remplace parseClaimsJws()
                .getPayload();                 // NOUVEAU : remplace getBody()
    }
}