package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.TaskCreateRequest;
import com.tasksphere.core.dto.TaskResponse;
import com.tasksphere.core.service.TaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/*
 * ====================================================================
 * LE CONTRÔLEUR REST (La Porte d'Entrée de l'API)
 * ====================================================================
 *
 * RÔLE ARCHITECTURAL :
 * Le contrôleur est le "Standardiste" de l'application. Il ne doit JAMAIS contenir
 * de logique métier (pas de if/else complexe, pas de calculs). Son seul but est de :
 * 1. Extraire des données de la requête HTTP (JSON -> Java).
 * 2. Déléguer au Service Métier.
 * 3. Transformer la réponse Java en JSON.
 *
 * @Slf4j : Génère un logger pour tracer les requêtes réseau.
 * @RestController : Dit à Spring "Les méthodes de cette classe renvoient du JSON, pas des pages HTML".
 * @RequestMapping : Définit la racine de l'URL pour ce contrôleur.
 * @RequiredArgsConstructor : Injecte les dépendances (ici, TaskManager) via le constructeur.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    // Le Service Métier (Le Chef). Le contrôleur ne connaît que lui, pas la BDD, pas l'IAM.
    private final TaskManager taskManager;

    /*
     * ====================================================================
     * ENDPOINT : CRÉATION D'UNE TÂCHE (Méthode HTTP POST)
     * ====================================================================
     *
     * @PostMapping : Lie cette méthode aux requêtes HTTP de type POST sur l'URL /api/v1/tasks.
     * @RequestBody : Magie de Spring. Il lit le corps de la requête HTTP (le JSON envoyé par le client),
     *                et le transforme automatiquement en objet Java (TaskCreateRequest).
     *
     * NOUVEAUTÉ V8 : @AuthenticationPrincipal
     * Quand la requête passe à travers le filtre JWT (JwtAuthenticationFilter de la V5),
     * Spring Security "authenticate" l'utilisateur et crée un objet Authentication.
     * En mettant cette annotation, Spring nous INJECTE directement cet objet dans la méthode.
     * Cela nous évite de devoir re-décoder le Token JWT manuellement pour savoir qui parle !
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @RequestBody TaskCreateRequest request,
            Authentication authentication) { // On utilise l'interface de base, c'est plus souple

        log.info("CONTROLEUR : Requête POST reçue pour créer la tâche : {}", request.title());

        // -------------------------------------------------------------
        // 1. EXTRACTION DU CONTEXTE SÉCURITÉ (V8)
        // -------------------------------------------------------------
        // L'objet Authentication contient tout ce que le filtre JWT a mis dedans.
        // .getName() retourne le "subject" du Token (dans notre cas, le username "saadoune").
        String currentUsername = authentication.getName();
        log.debug("CONTROLEUR : Demande initiée par l'utilisateur authentifié : {}", currentUsername);

        // -------------------------------------------------------------
        // 2. DÉLÉGATION AU SERVICE MÉTIER
        // -------------------------------------------------------------
        // On passe maintenant le username au Service. Le contrôleur s'en fiche de ce que
        // le Service va en faire (s'il va chercher en BDD, appeler l'IAM, etc.). Il délègue.
        Task createdTask = taskManager.createTask(
                request.title(),
                request.description(),
                currentUsername // On passe l'identité à la couche métier !
        );

        // -------------------------------------------------------------
        // 3. PRÉPARATION DE LA RÉPONSE HTTP
        // -------------------------------------------------------------
        // On traduit l'objet Domaine (Task) en DTO de sortie (TaskResponse) pour le client.
        TaskResponse response = TaskResponse.fromDomain(createdTask);

        // -------------------------------------------------------------
        // 4. CONSTRUCTION DE LA RÉPONSE REST
        // -------------------------------------------------------------
        // ResponseEntity permet de contrôler le code HTTP exact.
        // 201 Created : Le standard REST quand on crée une ressource avec succès.
        // URI.create(...) : On renvoie l'URL exacte de la nouvelle ressource dans le Header "Location".
        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + response.id()))
                .body(response);
    }

    /*
     * ====================================================================
     * ENDPOINT : LECTURE DE TOUTES LES TÂCHES (Méthode HTTP GET)
     * ====================================================================
     *
     * Ici, pas besoin de lire le corps de la requête.
     * Si on voulait sécuriser cet endpoint pour les admins uniquement, on ajouterait
     * une annotation @PreAuthorize("hasRole('ADMIN')") au-dessus de la méthode.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks() {
        log.info("CONTROLEUR : Requête GET reçue pour lister les tâches");

        // Délégation au Service
        List<Task> tasks = taskManager.getAllTasks();

        // Traduction Domaine -> DTO
        List<TaskResponse> responses = tasks.stream()
                .map(TaskResponse::fromDomain)
                .toList();

        // 200 OK : La requête a réussi et on retourne la liste.
        return ResponseEntity.ok(responses);
    }
}