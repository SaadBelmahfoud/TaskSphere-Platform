package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.TaskCreateRequest;
import com.tasksphere.core.dto.TaskStatusRequest;
import com.tasksphere.core.dto.TaskUpdateRequest;
import com.tasksphere.core.dto.TaskResponse;
import com.tasksphere.core.port.out.TaskPersistencePort;
import com.tasksphere.core.service.TaskManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : TaskController (API REST)
 * ═══════════════════════════════════════════════════════════════════
 *
 * ROLE : Exposer le domaine métier via HTTP REST.
 * Ce contrôleur est le SEUL point d'entrée HTTP pour les tâches.
 * Il ne contient AUCUNE logique métier — il délègue tout au TaskManager.
 *
 * ARCHITECTURE : Contrôleur → Service (TaskManager) → Port → Adaptateur
 * ─────────────────────────────────────────────────────────
 * Le contrôleur ne connaît ni la base de données, ni Hibernate.
 * Il ne travaille qu'avec des DTOs (entrées) et des TaskResponse (sorties).
 *
 * PRINCIPE DE MAPPING CENTRALISÉ (CORRECTION) :
 * ────────────────────────────────────────────────
 * AVANT : Le contrôleur construisait manuellement TaskResponse :
 *   new TaskResponse(id, title, desc, status, priority, dueDate,
 *                    completedAt, null, userId, assigneeId)
 *   → createdAt TOUJOURS null dans GET /tasks (liste)
 *   → createdAt = LocalDateTime.now() dans POST /tasks (création)
 *   → Incohérence entre endpoints
 *
 * APRÈS : Utilisation systématique de TaskResponse.fromDomain(task)
 *   → createdAt est TOUJOURS la vraie date de création
 *   → Un seul endroit à maintenir (DRY)
 *   → Cohérence garantie entre tous les endpoints
 *
 * ENDPOINTS :
 * ──────────
 * POST   /api/v1/tasks            → Créer une tâche
 * GET    /api/v1/tasks            → Lister/rechercher des tâches (RBAC)
 * GET    /api/v1/tasks/{id}       → Détail d'une tâche (RBAC)
 * PUT    /api/v1/tasks/{id}       → Modifier une tâche (RBAC)
 * PATCH  /api/v1/tasks/{id}/status → Changer le statut
 * PATCH  /api/v1/tasks/{id}/assign → Assigner une tâche (MANAGER/ADMIN)
 * DELETE /api/v1/tasks/{id}       → Supprimer (soft delete, RBAC)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskManager taskManager;

    /**
     * POST /api/v1/tasks — Créer une nouvelle tâche.
     *
     * @Valid : Active la validation Jakarta (annotations sur TaskCreateRequest).
     * Authentication : Injecté automatiquement par Spring Security
     * (contient l'email, le rôle, les authorities).
     *
     * RETOURNE 201 Created avec Location header.
     */
    @PostMapping
    public ResponseEntity<?> createTask(
            @Valid @RequestBody TaskCreateRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("CONTROLLER : POST /tasks — Création par {}", username);

        Task created = taskManager.createTask(
                request.title(), request.description(), username,
                request.priority(), request.dueDate(), request.assigneeId()
        );

        // CORRECTION : Utilisation de TaskResponse.fromDomain() au lieu de
        // la construction manuelle "new TaskResponse(...)".
        // ──────────────────────────────────────────────────────
        // AVANT : new TaskResponse(..., LocalDateTime.now(), ...)
        //   → createdAt = LocalDateTime.now() au lieu de la vraie date de création
        //   → La date de création était écrasée par "maintenant"
        //   → Si la tâche a été créée à T1 et la réponse construite à T2,
        //     createdAt serait T2 (faux !)
        //
        // APRÈS : TaskResponse.fromDomain(created)
        //   → Utilise task.createdAt() qui est la VRAIE date de création
        //   → Cohérent avec toutes les autres méthodes (getTaskById, etc.)
        TaskResponse response = TaskResponse.fromDomain(created);

        return ResponseEntity.created(URI.create("/api/v1/tasks/" + response.id())).body(response);
    }

    /**
     * GET /api/v1/tasks — Lister/rechercher les tâches avec filtres.
     *
     * RBAC :
     * - ADMIN/MANAGER → voient TOUTES les tâches (sans filtre userId)
     * - USER → ne voit que ses tâches + les tâches assignées
     *
     * Tous les @RequestParam sont optionnels (required = false).
     * Si un paramètre est absent, le filtre est ignoré (null).
     */
    @GetMapping
    public ResponseEntity<?> getTasks(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String dueDateFrom,
            @RequestParam(required = false) String dueDateTo,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = authentication.getName();
        String role = extractRole(authentication);
        log.info("CONTROLLER : GET /tasks — Liste pour {} (rôle: {}, page: {}, size: {})",
                username, role, page, size);

        if (size > 100) size = 100; // Sécurité : limiter la taille de page

        TaskPersistencePort.TaskSearchCriteria criteria =
                new TaskPersistencePort.TaskSearchCriteria(
                        keyword, null, assigneeId,
                        parseStatus(status), parsePriority(priority),
                        parseDate(dueDateFrom), parseDate(dueDateTo),
                        parseDateTime(createdFrom), parseDateTime(createdTo)
                );

        Page<Task> taskPage = taskManager.searchTasks(
                criteria, page, size, sortBy, sortDir, username, role);

        // CORRECTION : Utilisation de TaskResponse.fromDomain() au lieu de
        // la construction manuelle "new TaskResponse(..., null, ...)".
        // ──────────────────────────────────────────────────────
        // AVANT : new TaskResponse(id, title, desc, status, priority,
        //                          dueDate, completedAt, null, userId, assigneeId)
        //   → createdAt TOUJOURS null dans la liste !
        //   → Le dashboard "Créées cette semaine" ne pouvait pas filtrer
        //   → Le frontend ne pouvait pas afficher la date de création
        //
        // APRÈS : TaskResponse.fromDomain(task)
        //   → createdAt est la VRAIE date de création de chaque tâche
        var content = taskPage.getContent().stream()
                .map(task -> TaskResponse.fromDomain(task))  // ← CORRECTION : Utilise fromDomain() qui préserve createdAt
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
     * GET /api/v1/tasks/{id} — Récupérer une tâche par ID.
     *
     * RBAC : ADMIN/MANAGER voient toutes les tâches.
     * USER ne voit que ses propres tâches.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable String id,
                                         Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        Optional<Task> taskOpt = taskManager.getTaskById(id, username, role);
        if (taskOpt.isPresent()) {
            return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
    }

    // CORRECTION B8 : Ajout de @Valid sur la mise à jour
    // ────────────────────────────────────────────────
    // AVANT : @RequestBody TaskUpdateRequest request
    //   → Aucune validation ! Un titre de 1 ou 1000+ caractères passe.
    //   → Risk de données incohérentes en base
    //
    // APRÈS : @Valid @RequestBody TaskUpdateRequest request
    //   → Les annotations @Size, @NotBlank du DTO sont vérifiées
    //   → Si validation échoue → 400 Bad Request automatique
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable String id,
                                        @Valid @RequestBody TaskUpdateRequest request,  // ← CORRECTION B8
                                        Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        Optional<Task> taskOpt = taskManager.updateTask(id, username, role,
                request.title(), request.description(),
                request.priority(), request.dueDate());
        if (taskOpt.isPresent()) return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisée"));
    }

    /**
     * PATCH /api/v1/tasks/{id}/status — Changer le statut d'une tâche.
     *
     * PERMISSIONS : Le propriétaire (userId), l'assignataire (assigneeId),
     * MANAGER et ADMIN peuvent changer le statut.
     *
     * UTILISATION PAR LE KANBAN : Le frontend Kanban appelle cet endpoint
     * quand l'utilisateur fait un drag & drop d'une carte vers une autre colonne.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable String id,
                                              @Valid @RequestBody TaskStatusRequest request,
                                              Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        try { Task.TaskStatus.valueOf(request.status()); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Statut invalide. Valeurs possibles : TODO, DOING, DONE"));
        }
        Optional<Task> taskOpt = taskManager.updateTaskStatus(
                id, username, role, request.status());
        if (taskOpt.isPresent()) return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
    }

    /**
     * PATCH /api/v1/tasks/{id}/assign — Assigner une tâche à un utilisateur.
     *
     * RBAC : Seuls MANAGER et ADMIN peuvent assigner des tâches.
     *
     * CORPS DE LA REQUÊTE : { "assigneeId": "email@tasksphere.com" }
     *
     * Pour désassigner : { "assigneeId": "" } ou { "assigneeId": null }
     */
    @PatchMapping("/{id}/assign")
    public ResponseEntity<?> assignTask(@PathVariable String id,
                                        @RequestBody Map<String, String> request,
                                        Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        String assigneeId = request.get("assigneeId");

        if (!"ADMIN".equals(role) && !"MANAGER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "Seuls les rôles MANAGER et ADMIN peuvent assigner des tâches"));
        }

        Optional<Task> taskOpt = taskManager.assignTask(id, username, role, assigneeId);
        if (taskOpt.isPresent()) return ResponseEntity.ok(TaskResponse.fromDomain(taskOpt.get()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Tâche non trouvée"));
    }

    /**
     * DELETE /api/v1/tasks/{id} — Soft delete d'une tâche.
     *
     * RBAC : ADMIN peut supprimer n'importe quelle tâche.
     * USER ne peut supprimer que ses propres tâches.
     * La suppression est un SOFT DELETE (archivage).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable String id,
                                        Authentication authentication) {
        String username = authentication.getName();
        String role = extractRole(authentication);
        boolean deleted = taskManager.deleteTask(id, username, role);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Tâche non trouvée ou accès non autorisé"));
        }
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES PRIVÉES UTILITAIRES
    // ═══════════════════════════════════════════════════════

    /**
     * Extrait le rôle depuis les authorities de Spring Security.
     * Les authorities sont formatées "ROLE_USER", "ROLE_ADMIN", etc.
     * On retire le préfixe "ROLE_" pour obtenir le rôle brut.
     */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }

    /** Parse un string en TaskStatus, retourne null si invalide. */
    private Task.TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try { return Task.TaskStatus.valueOf(status); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Parse un string en TaskPriority, retourne null si invalide. */
    private Task.TaskPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) return null;
        try { return Task.TaskPriority.valueOf(priority); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Parse un string en LocalDate (yyyy-MM-dd), retourne null si invalide. */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try { return LocalDate.parse(dateStr); }
        catch (Exception e) { return null; }
    }

    /** Parse un string en LocalDateTime (ISO), retourne null si invalide. */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;
        try { return LocalDateTime.parse(dateTimeStr); }
        catch (Exception e) { return null; }
    }
}