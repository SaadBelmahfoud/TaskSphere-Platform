package com.tasksphere.core.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================
 * TESTS D'INTÉGRATION : AuthController
 * ====================================================================
 *
 * CONCEPT - Test d'intégration vs test unitaire :
 * ============================================
 * Un test d'intégration démarre TOUTE l'application Spring :
 * - Tous les beans (controllers, services, repositories)
 * - La base de données H2 en mémoire
 * - Spring Security avec ses filtres
 * - Le serveur Tomcat intégré
 *
 * L'avantage : on teste le vrai flux HTTP de bout en bout,
 * comme si un client curl faisait les requêtes.
 *
 * CONCEPT - @SpringBootTest(webEnvironment = RANDOM_PORT) :
 * Démarre l'appli sur un port aléatoire (pour éviter les conflits).
 * TestRestTemplate est automatiquement configuré pour pointer vers ce port.
 *
 * CONCEPT - @DirtiesContext(classMode = AFTER_CLASS) :
 * Redémarre le contexte Spring entre les classes de test.
 * Cela évite que les données d'un test ne polluent un autre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    // ============================================================
    // TESTS : LOGIN
    // ============================================================

    @Test
    @DisplayName("POST /api/v1/auth/login — Login avec identifiants corrects retourne 200 + tokens")
    void login_success_shouldReturn200WithTokens() {
        // ARRANGE
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "email", "saadoune@tasksphere.com",
                "password", "password123"
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        // ACT
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        // ASSERT
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("accessToken")).isNotNull();
        assertThat(responseBody.get("refreshToken")).isNotNull();
        assertThat(responseBody.get("tokenType")).isEqualTo("Bearer");
        assertThat(responseBody.get("expiresIn")).isEqualTo("3600");
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — Mauvais mot de passe retourne 401 avec message générique")
    void login_wrongPassword_shouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "email", "saadoune@tasksphere.com",
                "password", "mauvais-mot-de-passe"
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("error"))
                .isEqualTo("Email ou mot de passe incorrect");
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — Email inexistant retourne 401 avec MÊME message (anti-énumération)")
    void login_unknownEmail_shouldReturn401WithSameMessage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "email", "inconnu@tasksphere.com",
                "password", "password123"
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // MÊME message que pour mauvais mot de passe (sécurité : anti-énumération des comptes)
        assertThat(response.getBody().get("error"))
                .isEqualTo("Email ou mot de passe incorrect");
    }

    // ============================================================
    // TESTS : REFRESH TOKEN
    // ============================================================

    @Test
    @DisplayName("POST /api/v1/auth/refresh — Refresh token valide retourne nouveaux tokens (rotation)")
    void refresh_success_shouldReturnNewTokens() {
        // ARRANGE : d'abord se connecter pour obtenir un refresh token
        String refreshToken = loginAndGetRefreshToken("saadoune@tasksphere.com", "password123");

        // ACT : utiliser le refresh token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("refreshToken", refreshToken), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, Map.class);

        // ASSERT
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accessToken")).isNotNull();
        assertThat(response.getBody().get("refreshToken")).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — Réutilisation d'un token révoqué retourne 401 (anti-rejeu)")
    void refresh_revokedToken_shouldReturn401() {
        // ARRANGE : refresh une fois (le token est révoqué après rotation)
        String oldRefreshToken = loginAndGetRefreshToken("saadoune@tasksphere.com", "password123");

        // Premier refresh → le vieux token est révoqué, nouveau token créé
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> firstRefresh = new HttpEntity<>(
                Map.of("refreshToken", oldRefreshToken), headers);
        restTemplate.postForEntity("/api/v1/auth/refresh", firstRefresh, Map.class);

        // ACT : réutiliser le VIEUX token (déjà révoqué)
        HttpEntity<Map<String, String>> secondRefresh = new HttpEntity<>(
                Map.of("refreshToken", oldRefreshToken), headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", secondRefresh, Map.class);

        // ASSERT : doit être rejeté car le token a été révoqué par rotation
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — Token inexistant retourne 401")
    void refresh_invalidToken_shouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("refreshToken", "token-inexistant-qui-nexiste-pas"), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/refresh", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ============================================================
    // TESTS : LOGOUT
    // ============================================================

    @Test
    @DisplayName("POST /api/v1/auth/logout — Déconnexion révoque les tokens et retourne 200")
    void logout_success_shouldReturn200() {
        // ARRANGE : login pour obtenir un refresh token
        String refreshToken = loginAndGetRefreshToken("saadoune@tasksphere.com", "password123");

        // ACT : logout avec ce token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("refreshToken", refreshToken), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/logout", request, Map.class);

        // ASSERT
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("message")).isEqualTo("Déconnexion réussie");

        // Le refresh token ne doit PLUS fonctionner après logout
        HttpEntity<Map<String, String>> refreshRequest = new HttpEntity<>(
                Map.of("refreshToken", refreshToken), headers);
        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                "/api/v1/auth/refresh", refreshRequest, Map.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ============================================================
    // TESTS : PROFILS (3 rôles)
    // ============================================================

    @Test
    @DisplayName("POST /api/v1/auth/login — Les 3 profils (USER, MANAGER, ADMIN) peuvent se connecter")
    void login_allRoles_shouldReturn200() {
        String[] emails = {
                "saadoune@tasksphere.com",
                "manager@tasksphere.com",
                "admin@tasksphere.com"
        };

        for (String email : emails) {
            String refreshToken = loginAndGetRefreshToken(email, "password123");
            assertThat(refreshToken).isNotNull().isNotEmpty();
        }
    }

    // ============================================================
    // HELPER : méthode utilitaire pour se connecter et extraire le refresh token
    // ============================================================

    /**
     * Helper qui fait un login et retourne le refresh token.
     * Utilisé par les tests qui ont besoin d'un token valide.
     */
    private String loginAndGetRefreshToken(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("email", email, "password", password), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("refreshToken");
    }
}