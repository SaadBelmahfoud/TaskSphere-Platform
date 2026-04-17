package com.tasksphere.iam.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * ====================================================================
 * TESTS UNITAIRES : JwtService
 * ====================================================================
 *
 * CONCEPT - Test unitaire :
 * On teste UNE classe isolément, SANS Spring, SANS base de données.
 * On instancie la classe manuellement et on vérifie ses méthodes.
 *
 * CONCEPT - @Test JUnit 5 :
 * Chaque méthode annotée @Test est un cas de test autonome.
 * JUnit 5 exécute chaque méthode séparément.
 *
 * CONCEPT - AssertJ (assertThat) :
 * Syntaxe fluide pour les assertions : assertThat(valeur).isEqualTo(attendu)
 * Plus lisible que assertEquals(valeur, attendu).
 *
 * CONCEPT - @DisplayName :
 * Donner un nom lisible au test (apparaît dans les rapports).
 * Ex: "should generate valid JWT with correct username and role"
 */
class JwtServiceTest {

    private JwtService jwtService;

    /**
     * @BeforeEach s'exécute AVANT CHAQUE @Test.
     * On crée un JwtService frais pour chaque test (isolation).
     */
    @BeforeEach
    void setUp() {
        // On instancie JwtService directement (pas besoin de Spring)
        // Le constructeur prend le secret en paramètre (vide = clé auto-générée)
        jwtService = new JwtService("");
    }

    // ============================================================
    // TESTS : GÉNÉRATION
    // ============================================================

    @Test
    @DisplayName("Devrait générer un JWT non null et non vide")
    void shouldGenerateNonNullToken() {
        // ACT : générer un token
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        // ASSERT : le token ne doit être ni null ni vide
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Devrait générer un JWT contenant 3 parties (header.payload.signature)")
    void shouldGenerateTokenWithThreeParts() {
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        // Un JWT a toujours 3 parties séparées par des points
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
    }

    // ============================================================
    // TESTS : EXTRACTION
    // ============================================================

    @Test
    @DisplayName("Devrait extraire le bon username (email) du JWT")
    void shouldExtractCorrectUsername() {
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("saadoune@tasksphere.com");
    }

    @Test
    @DisplayName("Devrait extraire le bon rôle du JWT")
    void shouldExtractCorrectRole() {
        String token = jwtService.generateAccessToken("manager@tasksphere.com", "MANAGER");

        String role = jwtService.extractRole(token);

        assertThat(role).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Devrait extraire le rôle ADMIN correctement")
    void shouldExtractAdminRole() {
        String token = jwtService.generateAccessToken("admin@tasksphere.com", "ADMIN");

        String role = jwtService.extractRole(token);

        assertThat(role).isEqualTo("ADMIN");
    }

    // ============================================================
    // TESTS : VALIDATION
    // ============================================================

    @Test
    @DisplayName("Devrait valider un token avec le bon username")
    void shouldValidateTokenWithCorrectUsername() {
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        boolean isValid = jwtService.isTokenValid(token, "saadoune@tasksphere.com");

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Devrait rejeter un token si le username ne correspond pas")
    void shouldRejectTokenWithWrongUsername() {
        // On génère un token pour saadoune, mais on le valide avec un autre email
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        boolean isValid = jwtService.isTokenValid(token, "autre@tasksphere.com");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Un token fraîchement généré ne doit pas être expiré")
    void shouldNotBeExpiredForFreshToken() {
        String token = jwtService.generateAccessToken("saadoune@tasksphere.com", "USER");

        boolean isExpired = jwtService.isTokenExpired(token);

        assertThat(isExpired).isFalse();
    }

    // ============================================================
    // TESTS : REFRESH TOKEN
    // ============================================================

    @Test
    @DisplayName("Devrait générer un refresh token (UUID) non null")
    void shouldGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken();

        assertThat(refreshToken).isNotNull().isNotEmpty();

        // Un UUID a le format : xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertThat(refreshToken).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("Deux refresh tokens doivent être différents (unicité)")
    void shouldGenerateUniqueRefreshTokens() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        assertThat(token1).isNotEqualTo(token2);
    }
}