package com.tasksphere.iam.config.security;

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
 * Bien qu'on utilise actuellement le RBAC dans TaskManager (par programmation),
 * cette annotation permet de rajouter des sécurités au niveau méthode si besoin.
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
                // SESSION : stateless (pas de HttpSession)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // AUTORISATIONS PAR URL :
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics (pas de JWT requis)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Console H2 : réservée à l'ADMIN
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        // Swagger UI : public pour la documentation API
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/swagger-resources/**", "/webjars/**"
                        ).permitAll()
                        // Tout le reste : authentification JWT requise
                        .anyRequest().authenticated()
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