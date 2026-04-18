package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.TaskCreateRequest;
import com.tasksphere.core.dto.TaskStatusRequest;
import com.tasksphere.core.dto.TaskUpdateRequest;
import com.tasksphere.core.dto.TaskResponse;
import com.tasksphere.core.service.TaskManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/*
 * ====================================================================
 * CONTRÔLEUR REST : TASK (La porte d'entrée de l'API tâches)
 * ====================================================================
 *
 * PRINCIPE REST :
 * Chaque endpoint correspond à un verbe HTTP et une ressource :
 * - GET    /tasks          → Lister (200 OK, paginé)
 * - POST   /tasks          → Créer (201 Created avec Location header)
 * - GET    /tasks/{id}     → Détail (200 OK ou 404 Not Found)
 * - PUT    /tasks/{id}     → Modifier (200 OK ou 404)
 * - PATCH  /tasks/{id}/status → Changer statut (200 OK)
 * - DELETE /tasks/{id}     → Archiver (204 No Content)
 *
 * PRINCIPE @Valid :
 * Déclenche la validation Jakarta (@NotBlank, @Size...) automatiquement.
 * Si une validation échoue → MethodArgumentNotValidException → 400 Bad Request
 * avec le détail des erreurs (géré par GlobalExceptionHandler).
 *
 * PRINCIPE Authentication :
 * Spring Security remplit automatiquement ce paramètre après que
 * JwtAuthenticationFilter ait validé le JWT. On utilise authentication.getName()
 * pour récupérer le username du token JWT.
 *
 * PRINCIPE ResponseEntity<?> :
 * Le wildcard "?" est nécessaire car certains endpoints retournent soit
 * un TaskResponse (200 OK), soit un Map (404 NOT FOUND). Java ne sait pas
 * unifier ces 2 types dans un Optional.map().orElse(), donc on utilise
 * des if/else explicites à la place.
 *
 * PRINCIPE DE VERSIONNING D'API :
 * /api/v1/tasks → Le "v1" permet de faire évoluer l'API sans casser
 * les clients existants. On peut créer /api/v2/tasks avec un comportement différent.
 *
 * ARCHITECTURE : Le contrôleur ne contient AUCUNE logique métier.
 * Il délègue tout au service (TaskManager). Son rôle est uniquement :
 * 1. Recevoir la requête HTTP
 * 2. Valider les entrées (@Valid)
 * 3. Extraire le contexte sécurité (Authentication)
 * 4. Appeler le service
 * 5. Formater la réponse HTTP
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskManager taskManager;

    /**
     * ============================================================
     * POST /api/v1/tasks — Créer une tâche
     * ============================================================
     *
     * PRINCIPE 201 Created :
     * Quand on crée une ressource, on retourne 201 (pas 200).
     * Le header Location indique l'URL de la nouvelle ressource.
     *
     * PRINCIPE @Valid @RequestBody :
     * - @RequestBody : le JSON est dans le corps de la requête (pas l'URL)
     * - @Valid : déclenche la validation Jakarta Bean Validation
     */
    @PostMapping
    public ResponseEntity<?> createTask(
            @Valid @RequestBody TaskCreateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : POST /tasks — Création par {}", username);

        // On passe priority et dueDate au service pour une création complète
        Task created = taskManager.createTask(
                request.title(),
                request.description(),
                username,
                request.priority(),
                request.dueDate()
        );

        TaskResponse response = new TaskResponse(
                created.id(),
                created.title(),
                created.description(),
                created.status().name(),
                created.priority().name(),
                created.dueDate(),
                created.completedAt(),
                java.time.LocalDateTime.now(),
                created.userId()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + response.id()))
                .body(response);
    }

    /**
     * ============================================================
     * GET /api/v1/tasks — Lister MES tâches (paginées)
     * ============================================================
     *
     * PRINCIPE DE PAGINATION :
     * Query params : ?page=0&size=20
     * Par défaut : page 0, taille 20
     * On limite la taille à 100 pour éviter les requêtes trop lourdes.
     *
     * PRINCIPE @RequestParam(defaultValue) :
     * Si le client n'envoie pas le paramètre, la valeur par défaut est utilisée.
     */
    @GetMapping
    public ResponseEntity<?> getMyTasks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = authentication.getName();
        log.info("CONTROLLER : GET /tasks — Liste pour {} (page: {}, size: {})", username, page, size);

        // Sécurité : limiter la taille de page à 100 maximum
        if (size > 100) size = 100;

        Page<Task> taskPage = taskManager.getMyTasks(username, page, size);

        var content = taskPage.getContent().stream()
                .map(task -> new TaskResponse(
                        task.id(), task.title(), task.description(),
                        task.status().name(), task.priority().name(),
                        task.dueDate(), task.completedAt(), null, task.userId()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalElements", taskPage.getTotalElements(),
                "totalPages", taskPage.getTotalPages(),
                "number", taskPage.getNumber(),
                "size", taskPage.getSize()
        ));
    }

    /**
     * ============================================================
     * GET /api/v1/tasks/{id} — Détail d'une tâche
     * ============================================================
     * Seul le propriétaire peut voir sa tâche (ownership / RBAC).
     * Si la tâche n'existe pas ou n'appartient pas à l'utilisateur → 404.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTaskById(
            @PathVariable String id,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : GET /tasks/{} — par {}", id, username);

        Optional<Task> taskOpt = taskManager.getTaskById(id, username);
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
    }

    /**
     * ============================================================
     * PUT /api/v1/tasks/{id} — Modifier une tâche
     * ============================================================
     * Tous les champs sont optionnels. Seuls les champs fournis sont modifiés.
     * Seul le propriétaire peut modifier sa tâche (ownership / RBAC).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(
            @PathVariable String id,
            @RequestBody TaskUpdateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : PUT /tasks/{} — par {}", id, username);

        Optional<Task> taskOpt = taskManager.updateTask(
                id, username,
                request.title(), request.description(),
                request.priority(), request.dueDate()
        );
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisée"));
    }

    /**
     * ============================================================
     * PATCH /api/v1/tasks/{id}/status — Changer le statut
     * ============================================================
     * Body : { "status": "DOING" }
     *
     * NOTE : On valide manuellement que le statut est un enum valide
     * avant de passer au service. Cela évite une IllegalArgumentException
     * non interceptée qui donnerait une 500 au lieu d'une 400.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateTaskStatus(
            @PathVariable String id,
            @Valid @RequestBody TaskStatusRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : PATCH /tasks/{}/status → {} par {}", id, request.status(), username);

        // Valider que le statut est un enum valide (renvoyer 400 si invalide)
        try {
            Task.TaskStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Statut invalide. Valeurs possibles : TODO, DOING, DONE"));
        }

        Optional<Task> taskOpt = taskManager.updateTaskStatus(id, username, request.status());
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
    }

    /**
     * ============================================================
     * DELETE /api/v1/tasks/{id} — Archiver (soft delete)
     * ============================================================
     * 204 No Content = succès sans corps de réponse.
     * La tâche n'est pas supprimée physiquement, juste marquée comme archivée.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(
            @PathVariable String id,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : DELETE /tasks/{} — par {}", id, username);

        boolean deleted = taskManager.deleteTask(id, username);

        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
        }

        return ResponseEntity.noContent().build();
    }
}