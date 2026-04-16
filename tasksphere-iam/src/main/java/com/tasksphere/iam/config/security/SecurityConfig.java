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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * @Configuration : Indique que c'est une classe de configuration Spring.
 * @EnableWebSecurity : Désactive la configuration par défaut de Spring Boot et nous donne le contrôle total.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /*
     * LE BEAN LE PLUS IMPORTANT DE SÉCURITÉ.
     * C'est ici qu'on construit le mur, fil par fil.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. DÉSACTIVER CSRF : La protection contre les attaques de formulaires web.
                //    On le DÉSACTIVE car on fait une API REST (Stateless) qui utilise des Tokens, pas des Cookies.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. GESTION DES SESSIONS : On dit à Spring de NE JAMAIS créer de session en mémoire.
                //    L'état de connexion est porté par le JWT, pas par le serveur.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. LES RÈGLES D'ACCÈS (Les Authorization Rules)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll() // Le point d'entrée est OUVERT à tous
                        .requestMatchers("/h2-console/**").permitAll()  // Autorise H2
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Autorise Swagger
                        .requestMatchers("/api/v1/auth/**","/api/v1/iam/**").permitAll() // Le point d'entrée est OUVERT à tous
                        .anyRequest().authenticated()               // TOUT LE RESTE nécessite un token valide
                )



                // 4. INSÉRER NOTRE FILTRE CUSTOM.
                //    On dit à Spring : "Avant d'exécuter ton filtre par défaut qui vérifie les mots de passe,
                //    passe d'abord MON filtre à moi qui lit les JWT".
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Indispensable pour voir la console H2 dans les navigateurs modernes
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /*
     * Expose le gestionnaire d'authentification de Spring (utile si on devait faire un formulaire de login).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Ajoute cette méthode dans la classe SecurityConfig :
    @Bean
    public PasswordEncoder passwordEncoder() {
        // L'algorithme standard de l'industrie en 2026 pour les mots de passe.
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}