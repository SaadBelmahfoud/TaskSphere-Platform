package com.tasksphere.iam.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ═══════════════════════════════════════════════════════════════════
 * UTILITAIRE DE GESTION DES COOKIES HTTPONLY
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-5 : HttpOnly cookies pour les tokens
 *
 * PRINCIPE DES COOKIES HTTPONLY :
 * ────────────────────────────────
 * Un cookie HttpOnly est un cookie que JavaScript NE PEUT PAS lire
 * via document.cookie. Cela protège contre les attaques XSS :
 * - Si un attaquant injecte du JS malveillux, il ne peut pas
 *   voler le token d'authentification
 * - Le cookie est automatiquement envoyé par le navigateur
 *   avec chaque requête HTTP vers le domaine concerné
 *
 * PROPRIÉTÉS DE SÉCURITÉ :
 * ──────────────────────────
 * - HttpOnly : empêche l'accès via JavaScript (protection XSS)
 * - Secure : cookie envoyé UNIQUEMENT en HTTPS (protection MITM)
 * - SameSite=Lax : protection CSRF partielle
 *   - Lax = cookie envoyé sur les GET cross-site (navigation)
 *   - PAS envoyé sur les POST/PUT/DELETE cross-site
 *   - C'est un bon compromis entre sécurité et fonctionnalité
 * - Path : limite le cookie aux URLs correspondantes
 *   - accessToken : Path=/ → envoyé sur TOUTES les requêtes API
 *   - refreshToken : Path=/api/v1/auth/refresh → envoyé UNIQUEMENT
 *     sur l'endpoint de refresh (minimise l'exposition)
 *
 * POURQUOI SameSite=Lax ET PAS SameSite=Strict ?
 * ────────────────────────────────────────────────
 * - Strict : le cookie n'est JAMAIS envoyé cross-site
 *   → Problème : si un utilisateur clique sur un lien vers
 *     TaskSphere depuis un email, il devra se reconnecter
 * - Lax : le cookie est envoyé sur les GET cross-site
 *   → L'utilisateur reste connecté via un lien externe
 *   → Les POST/PUT/DELETE cross-site sont protégés
 *
 * POURQUOI SameSite=Lax ET PAS SameSite=None ?
 * ────────────────────────────────────────────────
 * - None : le cookie est TOUJOURS envoyé cross-site
 *   → Nécessite Secure=true (HTTPS uniquement)
 *   → En dev (HTTP localhost), SameSite=None ne fonctionne pas
 * - Lax fonctionne en HTTP et HTTPS → compatible dev + prod
 */
@Component
public class CookieHelper {

    /** Durée de vie de l'access token cookie en secondes (1 heure) */
    private static final int ACCESS_TOKEN_MAX_AGE = 3600;

    /** Durée de vie du refresh token cookie en secondes (7 jours) */
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 3600;

    /**
     * Environnement de production ou non.
     * En production : Secure=true, SameSite=Lax
     * En développement : Secure=false (HTTP localhost), SameSite=Lax
     */
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    /**
     * Ajoute les cookies HttpOnly pour l'access token et le refresh token.
     *
     * PRINCIPE DU SET-COOKIE HEADER :
     * ────────────────────────────────
     * Le serveur envoie un header HTTP :
     *   Set-Cookie: accessToken=xxx; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=3600
     * Le navigateur stocke le cookie et l'envoie automatiquement
     * avec chaque requête correspondant au Path.
     *
     * @param response     La réponse HTTP pour ajouter les cookies
     * @param accessToken  Le JWT access token (court terme, 1h)
     * @param refreshToken Le refresh token opaque (long terme, 7j)
     */
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        // Access Token Cookie
        String accessTokenCookie = buildCookie(
                "accessToken", accessToken,
                "/",                       // Path : envoyé sur toutes les routes
                ACCESS_TOKEN_MAX_AGE,      // Max-Age : 1 heure
                true,                      // HttpOnly
                cookieSecure               // Secure : true en prod (HTTPS)
        );
        response.addHeader("Set-Cookie", accessTokenCookie);

        // Refresh Token Cookie
        String refreshTokenCookie = buildCookie(
                "refreshToken", refreshToken,
                "/api/v1/auth",            // Path : limité aux endpoints auth
                REFRESH_TOKEN_MAX_AGE,     // Max-Age : 7 jours
                true,                      // HttpOnly
                cookieSecure               // Secure : true en prod (HTTPS)
        );
        response.addHeader("Set-Cookie", refreshTokenCookie);
    }

    /**
     * Supprime les cookies d'authentification (logout).
     *
     * PRINCIPE : Pour supprimer un cookie, on le recrée avec Max-Age=0.
     * Le navigateur supprime immédiatement le cookie.
     * Il faut utiliser les MÊMES propriétés (Path, Domain, Secure)
     * que lors de la création, sinon le navigateur ne le supprimera pas.
     */
    public void clearAuthCookies(HttpServletResponse response) {
        String accessTokenCookie = buildCookie(
                "accessToken", "",
                "/", 0, true, cookieSecure
        );
        response.addHeader("Set-Cookie", accessTokenCookie);

        String refreshTokenCookie = buildCookie(
                "refreshToken", "",
                "/api/v1/auth", 0, true, cookieSecure
        );
        response.addHeader("Set-Cookie", refreshTokenCookie);
    }

    /**
     * Construit un cookie au format Set-Cookie header.
     *
     * FORMAT RFC 6265 :
     *   Set-Cookie: name=value; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=3600
     *
     * PRINCIPE : On construit le cookie manuellement au lieu d'utiliser
     * javax.servlet.http.Cookie car l'API Cookie ne supporte pas
     * SameSite (ajouté dans les specs récentes).
     *
     * @param name     Nom du cookie
     * @param value    Valeur du cookie
     * @param path     Path du cookie (URL où il est envoyé)
     * @param maxAge   Durée de vie en secondes (0 = supprimer)
     * @param httpOnly HttpOnly flag
     * @param secure   Secure flag
     * @return La chaîne Set-Cookie complète
     */
    private String buildCookie(String name, String value, String path,
                               int maxAge, boolean httpOnly, boolean secure) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=").append(path);
        cookie.append("; Max-Age=").append(maxAge);
        cookie.append("; SameSite=Lax");
        if (httpOnly) cookie.append("; HttpOnly");
        if (secure) cookie.append("; Secure");
        return cookie.toString();
    }
}