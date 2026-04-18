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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/*
 * ====================================================================
 * CONFIGURATION SPRING SECURITY (Sécurité de l'application)
 * ====================================================================
 *
 * PRINCIPE SPRING SECURITY :
 * Spring Security est un filtre (Filter Chain) qui intercepte TOUTES les requêtes HTTP.
 * Chaque requête passe par la chaîne de filtres avant d'arriver au contrôleur.
 *
 * CHAÎNE DE FILTRES :
 * 1. CorsFilter : vérifie les règles CORS (origines autorisées)
 * 2. CsrfFilter : vérifie le token CSRF (désactivé pour REST API)
 * 3. JwtAuthenticationFilter : extrait et valide le JWT (notre filtre custom)
 * 4. AuthorizationFilter : vérifie les autorisations (roles)
 *
 * PRINCIPE STATELESS (sans session) :
 * SessionCreationPolicy.STATELESS = pas de HttpSession.
 * Chaque requête doit porter son propre token JWT.
 * Avantage : scalable (pas d'état côté serveur).
 *
 * PRINCIPE DES ENDPOINTS :
 * - permitAll() : accessible sans authentification (login, register, swagger)
 * - hasRole("ADMIN") : accessible uniquement aux administrateurs
 * - authenticated() : accessible à tout utilisateur authentifié
 *
 * ====================================================================
 * CORRECTION B9 — H2 CONSOLE : RESTREINDRE À ADMIN
 * ====================================================================
 *
 * PROBLÈME AVANT :
 *   .requestMatchers("/h2-console/**").permitAll()
 *   → N'importe qui pouvait accéder à la console H2 sans authentification !
 *   → La console H2 permet d'exécuter n'importe quelle requête SQL.
 *   → C'est une FAILLE CRITIQUE de sécurité en production.
 *
 * SOLUTION APRÈS :
 *   .requestMatchers("/h2-console/**").hasRole("ADMIN")
 *   → Seul un utilisateur avec le rôle ADMIN peut accéder à la console H2.
 *   → En dev : utiliser le compte admin/admin@tasksphere.com
 *   → En prod : la console H2 devrait être désactivée (spring.h2.console.enabled=false)
 *
 * PRINCIPE hasRole("ADMIN") :
 *   Spring Security ajoute automatiquement le préfixe "ROLE_" au rôle.
 *   hasRole("ADMIN") vérifie que l'utilisateur a l'autorité "ROLE_ADMIN".
 *   Notre JwtAuthenticationFilter extrait le rôle depuis le JWT et l'ajoute
 *   aux authorities avec le préfixe "ROLE_".
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Configure la chaîne de sécurité Spring Security.
     *
     * PRINCIPE DE CONSTRUCTION FLUIDE (Builder Pattern) :
     * HttpSecurity utilise le pattern Builder pour configurer la sécurité
     * étape par étape de manière lisible.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS : autoriser les requêtes depuis le frontend (localhost:3000)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF : désactivé pour les API REST (le JWT remplace le token CSRF)
                .csrf(AbstractHttpConfigurer::disable)

                // Sessions : STATELESS = pas de HttpSession (chaque requête porte son JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Autorisations par endpoint
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics (sans authentification)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // CORRECTION B9 : H2 Console réservée aux ADMIN uniquement
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")

                        // Swagger / API docs : publics (utile en dev)
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // Tous les autres endpoints : authentification requise
                        .anyRequest().authenticated()
                )

                // Ajouter notre filtre JWT AVANT le filtre d'authentification par défaut
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Headers : frameOptions.sameOrigin() requis pour la console H2
                // (H2 Console utilise des iframes qui sont bloquées par défaut)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Configuration CORS (Cross-Origin Resource Sharing).
     *
     * PRINCIPE CORS :
     * CORS est une sécurité du navigateur qui bloque les requêtes entre
     * des domaines différents. Par défaut, le navigateur bloque les requêtes
     * de http://localhost:3000 (frontend) vers http://localhost:8080 (backend).
     *
     * Cette configuration autorise explicitement le frontend à communiquer avec le backend.
     *
     * PRINCIPE setAllowCredentials(true) :
     * Autorise l'envoi de cookies et headers d'authentification.
     * Requis pour que le JWT soit envoyé dans le header Authorization.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));  // Frontend Next.js
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);  // Preflight cache : 1 heure

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * AuthenticationManager : gère le processus d'authentification.
     * Utilisé dans AuthController pour authentifier un utilisateur (username + password).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * PasswordEncoder : BCrypt pour hasher les mots de passe.
     *
     * PRINCIPE BCrypt :
     * - Algorithme de hashage lent (conçu pour résister aux attaques par force brute)
     * - Génère automatiquement un sel (salt) aléatoire
     * - Chaque hash est unique même pour le même mot de passe
     * - Vérification : passwordEncoder.matches(rawPassword, hashedPassword)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}