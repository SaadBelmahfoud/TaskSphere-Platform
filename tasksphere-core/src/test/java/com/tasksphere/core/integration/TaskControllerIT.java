package com.tasksphere.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================
 * TESTS D'INTÉGRATION : TaskController (CRUD complet)
 * ====================================================================
 *
 * CONCEPT - Test d'intégration complet :
 * ======================================
 * On teste TOUS les endpoints Tasks de bout en bout :
 * 1. Authentification via JWT
 * 2. CRUD des tâches
 * 3. Ownership (un utilisateur ne peut pas voir/modifier les tâches d'un autre)
 * 4. Soft delete (tâches archivées invisibles)
 * 5. Validation des entrées
 * 6. Pagination
 *
 * CONCEPT - ParameterizedTypeReference :
 * Utilisé pour désérialiser les réponses JSON complexes (ex: Map<String, Object>)
 * car Java perd le type générique à l'exécution (type erasure).
 *
 * CONCEPT - @BeforeEach :
 * S'exécute avant chaque test. On s'en sert pour récupérer un token JWT valide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private String userToken;    // Token JWT de saadoune (USER)
    private String managerToken; // Token JWT de manager (MANAGER)

    /**
     * Avant chaque test : on connecte les 2 utilisateurs pour avoir leurs tokens.
     * Cela simule un vrai scénario où l'utilisateur est déjà connecté.
     */
    @BeforeEach
    void setUp() {
        userToken = loginAndGetAccessToken("saadoune@tasksphere.com", "password123");
        managerToken = loginAndGetAccessToken("manager@tasksphere.com", "password123");
    }

    // ============================================================
    // TESTS : CRÉATION
    // ============================================================

    @Test
    @DisplayName("POST /api/v1/tasks — Créer une tâche avec succès retourne 201 Created")
    void createTask_success_shouldReturn201() {
        HttpHeaders headers = authHeaders(userToken);
        Map<String, Object> body = Map.of(
                "title", "Apprendre Spring Security",
                "description", "Comprendre JWT et BCrypt",
                "priority", "HIGH",
                "dueDate", "2026-05-01"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("id")).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo("Apprendre Spring Security");
        assertThat(response.getBody().get("status")).isEqualTo("TODO");
        assertThat(response.getBody().get("priority")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("POST /api/v1/tasks — Titre trop court retourne 400 Bad Request")
    void createTask_titleTooShort_shouldReturn400() {
        HttpHeaders headers = authHeaders(userToken);
        Map<String, Object> body = Map.of("title", "AB", "description", "Test");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/v1/tasks — Sans titre retourne 400 Bad Request")
    void createTask_noTitle_shouldReturn400() {
        HttpHeaders headers = authHeaders(userToken);
        Map<String, Object> body = Map.of("description", "Pas de titre");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/v1/tasks — Sans token retourne 401/403 (non authentifié)")
    void createTask_noAuth_shouldReturnUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("title", "Tâche sans auth");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isIn(
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ============================================================
    // TESTS : LISTE (PAGINATION)
    // ============================================================

    @Test
    @DisplayName("GET /api/v1/tasks — Lister mes tâches retourne 200 avec pagination")
    void getMyTasks_shouldReturn200WithPagination() {
        // D'abord créer une tâche
        createTask(userToken, "Tâche de test pagination", null, null, null);

        HttpHeaders headers = authHeaders(userToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = response.getBody();
        assertThat(body.get("totalElements")).isNotNull();
        assertThat(body.get("totalPages")).isNotNull();
        assertThat(body.get("content")).isNotNull();
    }

    // ============================================================
    // TESTS : DÉTAIL
    // ============================================================

    @Test
    @DisplayName("GET /api/v1/tasks/{id} — Récupérer une tâche par ID retourne 200")
    void getTaskById_success_shouldReturn200() {
        String taskId = createTask(userToken, "Tâche à récupérer", "Description", null, null);

        HttpHeaders headers = authHeaders(userToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Tâche à récupérer");
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} — Tâche inexistante retourne 404")
    void getTaskById_notFound_shouldReturn404() {
        HttpHeaders headers = authHeaders(userToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/00000000-0000-0000-0000-000000000000",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================
    // TESTS : OWNERSHIP (SECURITÉ)
    // ============================================================

    @Test
    @DisplayName("GET /api/v1/tasks/{id} — Accéder à la tâche d'un autre utilisateur retourne 404")
    void getTaskById_otherUser_shouldReturn404() {
        // saadoune crée une tâche
        String taskId = createTask(userToken, "Tâche privée de saadoune", null, null, null);

        // manager essaie d'y accéder → doit être rejeté
        HttpHeaders managerHeaders = authHeaders(managerToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(managerHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Le message ne doit PAS révéler que la tâche existe (sécurité)
    }

    @Test
    @DisplayName("PUT /api/v1/tasks/{id} — Modifier la tâche d'un autre retourne 404")
    void updateTask_otherUser_shouldReturn404() {
        String taskId = createTask(userToken, "Tâche de saadoune", null, null, null);

        HttpHeaders managerHeaders = authHeaders(managerToken);
        Map<String, Object> body = Map.of("title", "HACKED par manager");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.PUT,
                new HttpEntity<>(body, managerHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks/{id} — Supprimer la tâche d'un autre retourne 404")
    void deleteTask_otherUser_shouldReturn404() {
        String taskId = createTask(userToken, "Tâche de saadoune", null, null, null);

        HttpHeaders managerHeaders = authHeaders(managerToken);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.DELETE,
                new HttpEntity<>(managerHeaders), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================
    // TESTS : MISE À JOUR
    // ============================================================

    @Test
    @DisplayName("PUT /api/v1/tasks/{id} — Modifier titre et priorité retourne 200")
    void updateTask_success_shouldReturn200() {
        String taskId = createTask(userToken, "Tâche originale", "Desc", null, null);

        HttpHeaders headers = authHeaders(userToken);
        Map<String, Object> body = Map.of(
                "title", "Tâche modifiée",
                "priority", "CRITICAL"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Tâche modifiée");
        assertThat(response.getBody().get("priority")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("PUT /api/v1/tasks/{id} — MAJ partielle (uniquement dueDate) conserve le reste")
    void updateTask_partialUpdate_shouldKeepOtherFields() {
        String taskId = createTask(userToken, "Tâche", "Description originale", "HIGH", null);

        // Ne modifier QUE la date d'échéance
        HttpHeaders headers = authHeaders(userToken);
        Map<String, Object> body = Map.of("dueDate", "2026-06-15");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Tâche");
        assertThat(response.getBody().get("priority")).isEqualTo("HIGH");
        // dueDate est un objet dans le JSON, on vérifie juste que la réponse contient bien le champ
        assertThat(response.getBody().get("dueDate")).isNotNull();
    }

    // ============================================================
    // TESTS : CHANGEMENT DE STATUT
    // ============================================================

    @Test
    @DisplayName("PATCH /tasks/{id}/status — TODO → DOING retourne 200, completedAt = null")
    void updateStatus_todoToDoing_shouldReturn200() {
        String taskId = createTask(userToken, "Tâche", null, null, null);

        HttpHeaders headers = authHeaders(userToken);
        Map<String, String> body = Map.of("status", "DOING");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("DOING");
        assertThat(response.getBody().get("completedAt")).isNull();
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/status — DOING → DONE retourne 200, completedAt renseigné")
    void updateStatus_doingToDone_shouldSetCompletedAt() {
        // Créer et passer à DOING
        String taskId = createTask(userToken, "Tâche", null, null, null);
        changeStatus(userToken, taskId, "DOING");

        // Passer à DONE
        HttpHeaders headers = authHeaders(userToken);
        Map<String, String> body = Map.of("status", "DONE");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("DONE");
        assertThat(response.getBody().get("completedAt")).isNotNull();
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/status — Statut invalide retourne 400")
    void updateStatus_invalidStatus_shouldReturn400() {
        String taskId = createTask(userToken, "Tâche", null, null, null);

        HttpHeaders headers = authHeaders(userToken);
        Map<String, String> body = Map.of("status", "INVALIDE");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ============================================================
    // TESTS : SOFT DELETE
    // ============================================================

    @Test
    @DisplayName("DELETE /api/v1/tasks/{id} — Soft delete retourne 204 No Content")
    void deleteTask_success_shouldReturn204() {
        String taskId = createTask(userToken, "Tâche à supprimer", null, null, null);

        HttpHeaders headers = authHeaders(userToken);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("DELETE puis GET — Tâche soft-deleted ne doit plus apparaître (ni dans la liste, ni par ID)")
    void deleteTask_softDeleted_shouldBeInvisible() {
        String taskId = createTask(userToken, "Tâche fantôme", null, null, null);

        // Supprimer la tâche
        HttpHeaders headers = authHeaders(userToken);
        restTemplate.exchange("/api/v1/tasks/" + taskId, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        // Vérifier qu'elle n'apparaît PLUS par ID
        ResponseEntity<Map> getByIdResponse = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(getByIdResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /**
     * Helper : se connecte et retourne l'access token JWT.
     */
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

    /**
     * Helper : crée des HttpHeaders avec Authorization Bearer.
     * Chaque endpoint protégé a besoin de ce header.
     */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Helper : crée une tâche et retourne son ID.
     */
    private String createTask(String token, String title, String description,
                              String priority, String dueDate) {
        HttpHeaders headers = authHeaders(token);

        // Construire le body dynamiquement (certains champs peuvent être null)
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        if (description != null) body.put("description", description);
        if (priority != null) body.put("priority", priority);
        if (dueDate != null) body.put("dueDate", dueDate);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    /**
     * Helper : change le statut d'une tâche.
     */
    private void changeStatus(String token, String taskId, String status) {
        HttpHeaders headers = authHeaders(token);
        Map<String, String> body = Map.of("status", status);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ═══════════════════════════════════════════════════════════════
    // NOUVEAUX TESTS : ASSIGNATION + VISIBILITÉ PROPRIÉTAIRE+ASSIGNÉ
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /tasks/{id}/assign — MANAGER peut assigner une tâche")
    void assignTask_manager_shouldReturn200() {
        String taskId = createTask(managerToken, "Tâche à assigner", null, null, null);

        HttpHeaders headers = authHeaders(managerToken);
        Map<String, String> body = Map.of("assigneeId", "saadoune@tasksphere.com");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/assign", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("assigneeId")).isEqualTo("saadoune@tasksphere.com");
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/assign — USER ne peut PAS assigner (403)")
    void assignTask_user_shouldReturn403() {
        String taskId = createTask(managerToken, "Tâche non-assignable par USER", null, null, null);

        HttpHeaders headers = authHeaders(userToken);
        Map<String, String> body = Map.of("assigneeId", "admin@tasksphere.com");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/assign", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /tasks/{id} — Assigné peut voir la tâche (BUG 1 corrigé)")
    void getTaskById_assignee_shouldSeeTask() {
        // Manager crée une tâche assignée à saadoune
        String taskId = createTask(managerToken, "Tâche assignée à saadoune",
                null, null, "saadoune@tasksphere.com");

        // saadoune (USER) doit pouvoir voir cette tâche
        HttpHeaders userHeaders = authHeaders(userToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(userHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Tâche assignée à saadoune");
    }

    @Test
    @DisplayName("PUT /tasks/{id} — Assigné peut modifier la tâche (BUG 1 corrigé)")
    void updateTask_assignee_shouldUpdate() {
        String taskId = createTask(managerToken, "Tâche modifiable par assigné",
                null, null, "saadoune@tasksphere.com");

        HttpHeaders userHeaders = authHeaders(userToken);
        Map<String, Object> body = Map.of("description", "Modifiée par l'assigné");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId, HttpMethod.PUT,
                new HttpEntity<>(body, userHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/status — Assigné peut changer le statut")
    void updateStatus_assignee_shouldChange() {
        String taskId = createTask(managerToken, "Statut par assigné",
                null, null, "saadoune@tasksphere.com");

        HttpHeaders userHeaders = authHeaders(userToken);
        Map<String, String> body = Map.of("status", "DOING");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, userHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("DOING");
    }

    @Test
    @DisplayName("GET /tasks — La tâche assignée apparaît dans la liste de l'assigné")
    void getMyTasks_assignedTask_shouldAppear() {
        // Créer une tâche assignée à saadoune
        createTask(managerToken, "Visible pour l'assigné", null, null, "saadoune@tasksphere.com");

        // Vérifier que saadoune la voit dans sa liste
        HttpHeaders userHeaders = authHeaders(userToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(userHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> responseBody = response.getBody();
        var content = (java.util.List<?>) responseBody.get("content");
        boolean hasAssignedTask = content.stream()
                .anyMatch(t -> ((Map<?, ?>) t).get("title").equals("Visible pour l'assigné"));
        assertThat(hasAssignedTask).isTrue();
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/assign — Retirer l'assignation (unassign)")
    void unassignTask_shouldReturn200() {
        String taskId = createTask(managerToken, "Tâche à désassigner",
                null, null, "saadoune@tasksphere.com");

        HttpHeaders headers = authHeaders(managerToken);
        Map<String, String> body = Map.of("assigneeId", "");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/tasks/" + taskId + "/assign", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // L'assigneeId doit être null après désassignation
        assertThat(response.getBody().get("assigneeId")).isNull();
    }

    @Test
    @DisplayName("GET /dashboard/stats — Retourne les statistiques pour un USER")
    void dashboardStats_shouldReturn200() {
        createTask(userToken, "Tâche pour dashboard", null, "HIGH", null);

        HttpHeaders headers = authHeaders(userToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/dashboard/stats", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalTasks")).isNotNull();
        assertThat(response.getBody().get("tasksByStatus")).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register — Inscription réussie retourne 201")
    void register_success_shouldReturn201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "username", "newuser",
                "firstName", "New",
                "lastName", "User",
                "email", "newuser@test.com",
                "password", "password123",
                "confirmPassword", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/register", new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("accessToken")).isNotNull();
    }
}