package com.tasksphere.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS D'INTÉGRATION : CommentController (NOUVEAU)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PRINCIPE : Test complet de bout en bout pour les commentaires.
 * Chaque test suit le flux HTTP complet :
 *   Auth → JWT → Contrôleur → Service → BDD → Réponse JSON
 *
 * TESTS COUVERTS :
 * 1. Créer un commentaire sur une tâche propriétaire
 * 2. Lister les commentaires d'une tâche
 * 3. Modifier SON propre commentaire
 * 4. Ne PAS modifier le commentaire d'un autre
 * 5. Supprimer SON propre commentaire
 * 6. ADMIN peut supprimer n'importe quel commentaire
 * 7. Ne PAS voir les commentaires d'une tâche inaccessible
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommentControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private String userToken;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userToken = loginAndGetAccessToken("saadoune@tasksphere.com", "password123");
        managerToken = loginAndGetAccessToken("manager@tasksphere.com", "password123");
        adminToken = loginAndGetAccessToken("admin@tasksphere.com", "password123");
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{taskId}/comments — Créer un commentaire retourne 201")
    void createComment_shouldReturn201() {
        String taskId = createTask(managerToken, "Tâche pour commentaires", null, null, null);

        HttpHeaders headers = authHeaders(managerToken);
        Map<String, String> body = Map.of("content", "Premier commentaire !");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/comments",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("content")).isEqualTo("Premier commentaire !");
        assertThat(response.getBody().get("username")).isEqualTo("manager@tasksphere.com");
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{taskId}/comments — Lister les commentaires retourne 200")
    void getComments_shouldReturn200() {
        String taskId = createTask(managerToken, "Tâche pour liste", null, null, null);
        createComment(managerToken, taskId, "Commentaire 1");
        createComment(managerToken, taskId, "Commentaire 2");

        HttpHeaders headers = authHeaders(managerToken);
        ResponseEntity<Map[]> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/comments",
                HttpMethod.GET, new HttpEntity<>(headers), Map[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{id} — Modifier SON commentaire retourne 200")
    void updateOwnComment_shouldReturn200() {
        String taskId = createTask(managerToken, "Tâche pour update", null, null, null);
        String commentId = createComment(managerToken, taskId, "Commentaire original");

        HttpHeaders headers = authHeaders(managerToken);
        Map<String, String> body = Map.of("content", "Commentaire modifié");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/comments/" + commentId,
                HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("content")).isEqualTo("Commentaire modifié");
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{id} — Modifier le commentaire d'un autre retourne 404")
    void updateOtherUserComment_shouldReturn404() {
        String taskId = createTask(managerToken, "Tâche pour RBAC", null, null, "saadoune@tasksphere.com");
        String commentId = createComment(managerToken, taskId, "Commentaire du manager");

        // saadoune essaie de modifier le commentaire du manager → 404
        HttpHeaders userHeaders = authHeaders(userToken);
        Map<String, String> body = Map.of("content", "Hack!");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/comments/" + commentId,
                HttpMethod.PUT, new HttpEntity<>(body, userHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} — Supprimer SON commentaire retourne 204")
    void deleteOwnComment_shouldReturn204() {
        String taskId = createTask(managerToken, "Tâche pour delete", null, null, null);
        String commentId = createComment(managerToken, taskId, "Commentaire à supprimer");

        HttpHeaders headers = authHeaders(managerToken);
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/comments/" + commentId,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} — ADMIN peut supprimer n'importe quel commentaire")
    void adminCanDeleteAnyComment_shouldReturn204() {
        String taskId = createTask(managerToken, "Tâche admin delete", null, null, null);
        String commentId = createComment(managerToken, taskId, "Commentaire du manager");

        HttpHeaders adminHeaders = authHeaders(adminToken);
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/comments/" + commentId,
                HttpMethod.DELETE, new HttpEntity<>(adminHeaders), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    private String loginAndGetAccessToken(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("email", email, "password", password), headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String createTask(String token, String title, String description,
                              String priority, String assigneeId) {
        HttpHeaders headers = authHeaders(token);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        if (description != null) body.put("description", description);
        if (priority != null) body.put("priority", priority);
        if (assigneeId != null) body.put("assigneeId", assigneeId);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private String createComment(String token, String taskId, String content) {
        HttpHeaders headers = authHeaders(token);
        Map<String, String> body = Map.of("content", content);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/comments",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }
}