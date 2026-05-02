package com.tasksphere.iam.config.security;

/*
 * ====================================================================
 * LES IMPORTS (La gestion des dépendances)
 * ====================================================================
 * ATTENTION : En Spring Boot 3.x, on utilise JAKARTA (jakarta.servlet.*),
 * plus jamais JAVAX (javax.servlet.*). C'est une rupture obligatoire.
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Imports spécifiques à la librairie JJWT (Gestion des erreurs cryptographiques)
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;

// Imports Spring & Lombok
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * ====================================================================
 * LA CLASSE (Le Filtre de Sécurité)
 * ====================================================================
 *
 * DESIGN PATTERN : Chain of Responsibility (Chaîne de responsabilité).
 * Ce filtre est le premier maillon de la chaîne de sécurité. Il intercepte TOUTES les requêtes HTTP.
 *
 * @Component : Indique à Spring que c'est un bean de configuration/infrastructure.
 * @Slf4j : Génère un logger "log" pour tracer ce qui se passe (indispensable en sécurité).
 * @RequiredArgsConstructor : Injecte les dépendances finales via le constructeur (Injection propre).
 * OncePerRequestFilter : Garantit que ce filtre ne s'exécute qu'une seule et unique fois par requête,
 *                         même si la requête est redirigée en interne par Spring.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PHASE 1 — CORRECTION P1-5 : Lecture des cookies HttpOnly
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT :
 *   Le filtre ne lisait QUE le header Authorization: Bearer <token>.
 *   → Les cookies HttpOnly n'étaient JAMAIS vérifiés.
 *   → Même si le serveur envoyait des cookies, le filtre les ignorait.
 *   → L'authentification par cookie ne fonctionnait PAS.
 *
 * APRÈS :
 *   Le filtre cherche le JWT en DEUX endroits, par ordre de priorité :
 *   1. Header Authorization: Bearer <token> (priorité haute)
 *   2. Cookie accessToken=<token> (fallback si pas de header)
 *
 *   PRIORITÉ AU HEADER :
 *   - Si les deux existent, le header gagne (comportement standard)
 *   - Permet de tester avec curl/Postman en envoyant le header
 *   - Le cookie est un fallback pour les navigateurs
 *
 *   PRINCIPE DE SÉCURITÉ :
 *   - Le cookie est HttpOnly → JavaScript NE PEUT PAS le lire
 *   - Le cookie est envoyé automatiquement par le navigateur
 *   - Pas besoin de header Authorization manuel côté frontend
 *   - Le cookie Path=/ est envoyé sur TOUTES les routes API
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // ====================================================================
    // DÉPENDANCES
    // ====================================================================

    // Service maison qui sait lire et vérifier la cryptographie du token.
    private final JwtService jwtService;

    // Service standard de Spring Security pour charger un utilisateur depuis... n'importe où (BDD, mémoire, etc.)
    private final UserDetailsService userDetailsService;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 : Nom du cookie access token
     * ═══════════════════════════════════════════════════════════════════
     * Ce nom DOIT être identique à celui défini dans CookieHelper.
     * Si on le change ici, il faut le changer dans CookieHelper aussi.
     * ═══════════════════════════════════════════════════════════════════
     */
    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    /*
     * ====================================================================
     * LA MÉTHODE CŒUR (L'interception)
     * ====================================================================
     * Cette méthode est appelée automatiquement par Tomcat pour CHAQUE requête HTTP entrante.
     *
     * @param request : La requête HTTP entrante (celle du client).
     * @param response : La réponse HTTP (pour renvoyer des erreurs si besoin, mais on évitera).
     * @param filterChain : L'objet qui représente le RESTE de la chaîne de filtres (le maillon suivant).
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        log.debug("FILTRE SECURITE : Interception de la requête {}", request.getRequestURI());

        // -------------------------------------------------------------
        // ÉTAPE 1 : Extraction du JWT — Header Authorization OU Cookie
        // -------------------------------------------------------------
        // ═══════════════════════════════════════════════════════════════════
        // PHASE 1 — P1-5 : Double source de JWT
        // ═══════════════════════════════════════════════════════════════════
        //
        // CHEMIN 1 : Header Authorization (priorité haute)
        //   Format : "Bearer eyJhbGciOi..."
        //   Utilisé par : curl, Postman, clients mobiles, API
        //
        // CHEMIN 2 : Cookie HttpOnly (fallback)
        //   Format : accessToken=eyJhbGciOi...
        //   Utilisé par : navigateurs web (envoi automatique)
        //
        // LOGIQUE :
        //   if (header existe) → utiliser le header
        //   else if (cookie existe) → utiliser le cookie
        //   else → pas de JWT → passer au filtre suivant (anonyme)
        //
        // POURQUOI LE HEADER A LA PRIORITÉ ?
        //   - Permet les tests avec curl -H "Authorization: Bearer xxx"
        //   - Les clients mobiles utilisent toujours le header
        //   - Le cookie est un confort pour les navigateurs
        // ═══════════════════════════════════════════════════════════════════
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // CHEMIN 1 : Token dans le header Authorization
            jwt = authHeader.substring(7);
            log.debug("FILTRE SECURITE : JWT trouvé dans le header Authorization");
        } else {
            // ═══════════════════════════════════════════════════════
            // CHEMIN 2 : Token dans le cookie HttpOnly (P1-5)
            // ═══════════════════════════════════════════════════════
            // request.getCookies() retourne null s'il n'y a aucun cookie.
            // On itère pour trouver le cookie nommé "accessToken".
            //
            // PRINCIPE Jakarta Servlet :
            // Cookie[] getCCookies() retourne TOUS les cookies envoyés
            // par le navigateur pour ce Path. Les cookies HttpOnly sont
            // inclus (HttpOnly empêche l'accès via JavaScript, PAS via
            // le code serveur Java).
            // ═══════════════════════════════════════════════════════
            String cookieToken = extractTokenFromCookies(request);
            if (cookieToken != null) {
                jwt = cookieToken;
                log.debug("FILTRE SECURITE : JWT trouvé dans le cookie HttpOnly");
            } else {
                // Aucun token trouvé (ni header, ni cookie)
                log.debug("FILTRE SECURITE : Pas de token trouvé (ni header, ni cookie), on passe au filtre suivant");
                filterChain.doFilter(request, response);
                return;
            }
        }

        // -------------------------------------------------------------
        // ÉTAPE 2 : Le Bouclier Anti-Crash (Try/Catch)
        // -------------------------------------------------------------
        // RÈGLE D'OR DE SÉCURITÉ : On ne laisse JAMAIS une exception de cryptographie remonter
        // jusqu'au client ou polluer les logs en erreur (ERROR). Un attaquant pourrait forger un token
        // pour provoquer des erreurs de parsing et saturer les logs (Log Forging).
        try {
            // On demande à notre service de lire le token pour trouver le username
            username = jwtService.extractUsername(jwt);

            // On vérifie si Spring a déjà authentifié quelqu'un pour cette requête
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // On demande au UserDetailsService de charger l'utilisateur correspondant
                // (Dans notre cas, ça va chercher dans le stub "UserDetailsConfig")
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // On vérifie mathématiquement que la signature du token correspond bien à cet utilisateur
                if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                    log.info("FILTRE SECURITE : Token valide pour l'utilisateur {}", username);

                    // -------------------------------------------------------------
                    // ÉTAPE 3 : La Création du Ticket d'Entrée
                    // -------------------------------------------------------------
                    // Si on arrive ici, l'utilisateur est légitime. On crée un objet d'authentification Spring.
                    // On lui donne ses droits (Authorities) récupérés depuis le UserDetails.
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null, // Pas de mot de passe ici, le JWT prouve l'identité
                            userDetails.getAuthorities()
                    );

                    // On attache des détails techniques à ce ticket (ex: l'adresse IP du client)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // -------------------------------------------------------------
                    // ÉTAPE 4 : Le Contexte de Sécurité (La Magie de Spring)
                    // -------------------------------------------------------------
                    // On place le ticket dans le "SecurityContextHolder".
                    // C'est un objet global attaché au Thread en cours (ThreadLocal).
                    // À partir de cet instant, n'importe où dans le code (ex: dans TaskController),
                    // on pourra faire "SecurityContextHolder.getContext().getAuthentication()" pour savoir qui est connecté !
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (SignatureException e) {
            // Le token a été modifié, la signature cryptographique ne correspond plus.
            // C'est une tentative d'attaque (ou un token obsolète). On log en "WARN" (avertissement) pour monitorer.
            log.warn("FILTRE SECURITE : Signature JWT invalide. Tentative de falsification ou token corrompu. Rejet silencieux.");
        } catch (ExpiredJwtException e) {
            // Le token est valide cryptographiquement, mais sa date d'expiration est dépassée.
            // C'est normal (l'utilisateur a laissé sa session expirer). On log en "WARN".
            log.warn("FILTRE SECURITE : Le token JWT a expiré. Rejet silencieux.");
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // Quelqu'un a un token valide, mais l'utilisateur a été supprimé de la base depuis.
            // On le traite comme une tentative d'intrusion ou une session obsolète.
            log.warn("FILTRE SECURITE : Token valide, mais l'utilisateur n'existe plus en base. Rejet silencieux.");
        }
        catch (Exception e) {
            // Attrape-par-tout pour les erreurs inattendues (ex: format du token complètement cassé).
            // On log en "ERROR" car c'est une anomalie technique qui nécessite l'attention de l'équipe.
            log.error("FILTRE SECURITE : Erreur inattendue lors de l'analyse du token", e);
        }

        // -------------------------------------------------------------
        // ÉTAPE 5 : Passer la main (OBLIGATOIRE)
        // -------------------------------------------------------------
        // ATTENTION : Quoi qu'il se passe (bon token, mauvais token, ou exception interceptée),
        // on DOIT appeler filterChain.doFilter().
        // Si on ne l'appelle pas, la requête meurt ici et le client aura un "Connection Reset".
        // Si le token est invalide, le SecurityContextHolder sera vide. Spring Security s'en rendra
        // un peu plus loin dans la chaîne et renverra automatiquement une réponse HTTP 401 Unauthorized.
        filterChain.doFilter(request, response);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PHASE 1 — P1-5 : Extraction du JWT depuis les cookies
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE :
     * Le navigateur envoie automatiquement les cookies avec chaque
     * requête correspondant au Path du cookie. Le cookie "accessToken"
     * a Path=/ → il est envoyé sur TOUTES les requêtes.
     *
     * HttpOnly : empêche JavaScript d'accéder au cookie (anti-XSS)
     * mais le code serveur Java PEUT le lire via getCookies().
     *
     * SÉCURITÉ :
     * - Le cookie est envoyé SEULEMENT sur le même domaine (SameSite=Lax)
     * - Le cookie ne peut PAS être lu par JavaScript (HttpOnly)
     * - Le cookie est vérifié par la même logique JWT que le header
     *
     * @param request La requête HTTP contenant les cookies
     * @return Le JWT trouvé dans le cookie, ou null si absent
     * ═══════════════════════════════════════════════════════════════════
     */
    private String extractTokenFromCookies(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}