package com.tasksphere.iam.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * ====================================================================
 * CONFIGURATION DE SÉCURITÉ (Le mur d'enceinte)
 * ====================================================================
 *
 * PRINCIPE :
 * Cette classe définit QUI a accès à QUOI.
 * - permitAll() = accès public (pas besoin de JWT)
 * - authenticated() = accès protégé (JWT obligatoire)
 *
 * SPRINT 1 : Mise à jour des endpoints publics pour le refresh et le login.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Désactiver CSRF (pas de formulaires web, on utilise des tokens)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Sessions stateless (le JWT porte l'état, pas le serveur)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Règles d'accès
                .authorizeHttpRequests(auth -> auth
                        // Endpoints PUBLICS (login, refresh, logout)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // H2 console (dev only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Swagger (dev only)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // TOUT LE RESTE nécessite un JWT valide
                        .anyRequest().authenticated()
                )

                // 4. Insérer notre filtre JWT avant le filtre par défaut
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 5. Autoriser les frames pour la console H2
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Bean pour le hashage des mots de passe.
     * BCrypt est l'algorithme standard de l'industrie.
     * Il est lent par conception (~100ms) pour rendre les attaques par force brute coûteuses.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}