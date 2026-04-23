package com.tasksphere.iam.config.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * CONFIGURATION SPRING SECURITY
 * ═══════════════════════════════════════════════════════════════════
 *
 * ARCHITECTURE DE SÉCURITÉ :
 * ────────────────────────────
 * ┌──────────────┐    ┌────────────────────┐    ┌──────────────┐
 * │   Requête    │ →  │ JwtAuthFilter      │ →  │ Controller   │
 * │   HTTP       │    │ (vérifie le JWT)   │    │              │
 * └──────────────┘    └────────────────────┘    └──────────────┘
 *
 * CHAÎNE DE FILTRES (Security Filter Chain) :
 * ────────────────────────────────────────────
 * 1. CorsFilter → gère le CORS (origines autorisées)
 * 2. CsrfFilter → DÉSACTIVÉ (on utilise des JWT, pas des sessions)
 * 3. JwtAuthenticationFilter → extrait et valide le JWT
 * 4. UsernamePasswordAuthenticationFilter → PAS utilisé (pas de form login)
 *
 * SESSION MANAGEMENT : STATELESS
 * ─────────────────────────────────
 * Chaque requête est indépendante. Le serveur ne stocke PAS de session.
 * L'état d'authentification est porté par le JWT dans le header Authorization.
 *
 * @EnableMethodSecurity :
 * ──────────────────────
 * Permet d'utiliser @PreAuthorize sur les méthodes des contrôleurs.
 * Exemple : @PreAuthorize("hasRole('ADMIN')") sur une méthode
 * Utilisé notamment par AdminController (Section 6) pour protéger
 * les endpoints d'administration des utilisateurs.
 *
 * ═══════════════════════════════════════════════════════════════════
 * SECTION 6 — ENDPOINTS COLLABORATION
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAUX ENDPOINTS AJOUTÉS (Section 6) :
 * ─────────────────────────────────────────
 * COMMENTS :
 *   GET    /api/v1/tasks/{taskId}/comments    → Lecture (authentifié)
 *   POST   /api/v1/tasks/{taskId}/comments    → Création (authentifié)
 *   DELETE /api/v1/tasks/{taskId}/comments/{id} → Suppression (owner ou ADMIN)
 *
 * ACTIVITY LOG :
 *   GET /api/v1/tasks/{taskId}/activity       → Lecture par tâche (authentifié)
 *   GET /api/v1/activity?page=0&size=20       → Lecture globale (ADMIN/MANAGER)
 *
 * DASHBOARD :
 *   GET /api/v1/dashboard/stats               → Stats (authentifié, RBAC interne)
 *
 * ADMIN :
 *   GET   /api/v1/admin/users                  → Liste utilisateurs (ADMIN only)
 *   PATCH /api/v1/admin/users/{id}/role        → Changement rôle (ADMIN only)
 *   PATCH /api/v1/admin/users/{id}/status      → Activation/désactivation (ADMIN only)
 *
 * CONFIGURATION :
 * ──────────────
 * Tous ces endpoints sont couverts par `.anyRequest().authenticated()`.
 * AUCUNE nouvelle règle n'est nécessaire pour les endpoints métiers.
 *
 * Seuls les endpoints ADMIN utilisent @PreAuthorize("hasRole('ADMIN')")
 * directement sur les méthodes du contrôleur (AdminController).
 * C'est une double protection : URL filter chain + annotation méthode.
 *
 * CORRECTIF SECTION 6 :
 * ────────────────────
 * La console H2 (/h2-console/**) passe de hasRole("ADMIN") à permitAll().
 * Raison : la console H2 ne peut pas envoyer de JWT → boucle de redirect.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS : autoriser le frontend localhost:3000
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF : désactivé car on utilise des JWT stateless
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                // SESSION : stateless (pas de HttpSession)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // AUTORISATIONS PAR URL :
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics (pas de JWT requis)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // ═══════════════════════════════════════════════════════
                        // [Section 6] CORRECTIF : Console H2 — permitAll()
                        // ═══════════════════════════════════════════════════════
                        //
                        // AVANT (bug) : .hasRole("ADMIN")
                        // → La console H2 ne peut pas envoyer de JWT token
                        //   (c'est une interface HTML qui s'authentifie via
                        //    les paramètres de connexion H2, pas via Spring Security)
                        // → Spring Security redirige vers /login → boucle infinie
                        //
                        // APRÈS (correction) : .permitAll()
                        // → La console H2 est accessible sans JWT
                        // → L'authentification H2 est gérée par H2 lui-même
                        //   (spring.datasource.url avec user/password)
                        //
                        // ⚠️ SÉCURITÉ EN PRODUCTION :
                        // → Désactiver la console H2 : spring.h2.console.enabled=false
                        // → Ou retirer cette ligne si H2 n'est pas utilisé en prod
                        // → En production, on utilise PostgreSQL, pas H2
                        // ═══════════════════════════════════════════════════════
                        .requestMatchers("/h2-console/**").permitAll()
                        // Swagger UI : public pour la documentation API
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/swagger-resources/**", "/webjars/**"
                        ).permitAll()
                        // ═══════════════════════════════════════════════════════
                        // ENDPOINTS SECTION 6 — Couverts par anyRequest().authenticated()
                        // ═══════════════════════════════════════════════════════
                        // Pas besoin de règles spécifiques pour :
                        // - /api/v1/tasks/{id}/comments/**  → Couvert par authenticated()
                        // - /api/v1/tasks/{id}/activity    → Couvert par authenticated()
                        // - /api/v1/activity              → Couvert par authenticated()
                        // - /api/v1/dashboard/**          → Couvert par authenticated()
                        // - /api/v1/admin/**              → Couvert par authenticated()
                        //                                   + @PreAuthorize("hasRole('ADMIN')")
                        //                                   sur les méthodes AdminController
                        //
                        // Le RBAC métier (USER/MANAGER/ADMIN) est géré PROGRAMMATIQUEMENT
                        // dans les services (TaskManager, CommentService, DashboardService)
                        // et via @PreAuthorize sur AdminController.
                        // ═══════════════════════════════════════════════════════
                        // Tout le reste : authentification JWT requise
                        .anyRequest().authenticated()
                )
                // ═══════════════════════════════════════════════════════
                // CORRECTIF CRITIQUE : AuthenticationEntryPoint personnalisé
                // ═══════════════════════════════════════════════════════
                //
                // PROBLÈME SANS CETTE LIGNE :
                // Spring Security 6.x retourne 403 (Forbidden) par défaut
                // quand un utilisateur non-authentifié accède à /api/v1/tasks.
                // Le frontend intercepteur ne déclenche le refresh QUE sur 401.
                // → Le 403 est traité comme une erreur métier → pas de refresh
                // → L'utilisateur reste bloqué avec une erreur 403
                //
                // AVEC CETTE LIGNE :
                // Spring Security retourne 401 Unauthorized avec un body JSON.
                // Le frontend intercepteur capte le 401 → refresh token → retry.
                // ═══════════════════════════════════════════════════════
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized\",\"message\":\"JWT token requis ou invalide\"}"
                            );
                        })
                )
                // AJOUT DU FILTRE JWT avant le filtre par défaut
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // HEADERS : autoriser les iframes pour la console H2
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Configuration CORS (Cross-Origin Resource Sharing).
     *
     * POURQUOI CORS ?
     * Le frontend tourne sur localhost:3000 (Next.js)
     * Le backend tourne sur localhost:8080 (Spring Boot)
     * Sans CORS, le navigateur bloque les requêtes cross-origin.
     *
     * PRODUCTION : Remplacer localhost:3000 par le domaine réel.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);  // Autoriser les cookies/credentials
        configuration.setMaxAge(3600L);           // Préflight cache pendant 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * AuthenticationManager : nécessaire pour l'authentification programmatique.
     * Utilisé par JwtAuthenticationFilter pour vérifier le token.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCryptPasswordEncoder : algorithme de hachage pour les mots de passe.
     *
     * POURQUOI BCrypt ?
     * - Auto-salt : un sel aléatoire est généré pour chaque mot de passe
     * - Lent par conception : résiste aux attaques par force brute
     * - Adaptable : le facteur de coût peut être augmenté (12 par défaut)
     *
     * Le mot de passe N'EST JAMAIS stocké en clair en base.
     * Seul le hash BCrypt est stocké (60 caractères).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}