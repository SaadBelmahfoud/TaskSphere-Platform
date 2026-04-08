package com.tasksphere.core.controller;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.dto.TaskCreateRequest;
import com.tasksphere.core.dto.TaskResponse;
import com.tasksphere.core.service.TaskManager; // Le contrôleur parle au Service, JAMAIS au Domaine
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/*
 * @RestController : Annotation MAGIQUE.
 * Elle dit à Spring : "Quand tu vois cette classe, je veux que ses méthodes
 * répondent à des requêtes HTTP, et que les objets renvoyés soient automatiquement
 * transformés en JSON".
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks") // Définit l'URL de base de ce contrôleur
@RequiredArgsConstructor // Annotation Lombok (voir explication dessous)
public class TaskController {

    // Dépendance. On a besoin du Chef (Service) pour travailler.
    private final TaskManager taskManager;

    /*
     * EXPLICATION DE @RequiredArgsConstructor (Lombok)
     * Dans la V1, on avait écrit : public TaskManager() { ... }
     * Ici, on ne met aucun constructeur. Mais on a mis l'attribut "final" devant taskManager.
     * Lombok va voir ça, et GENERER AUTOMATIQUEMENT un constructeur avec taskManager en paramètre.
     * Spring IoC (qu'on a vu en V1) va intercepter ce constructeur et injecter le vrai Service.
     * Avantage : 0 ligne de code boilerplate, et on garde l'injection par constructeur (Best Practice).
     */

    /*
     * ANNOTATIONS HTTP :
     * @PostMapping : Réagit UNIQUEMENT aux requêtes HTTP de type POST (création).
     * @RequestBody : Magie de Spring. Il va lire le corps de la requête HTTP (le JSON envoyé
     * par le client), et le transformer TOUT SEUL en objet Java TaskCreateRequest.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskCreateRequest request) {
        log.info("Reçu demande de création pour : {}", request.title());

        // 1. Le serveur demande au chef de créer le plat (Domaine)
        Task createdTask = taskManager.createTask(request.title(), request.description());

        // 2. Le serveur met le plat sur un plateau (DTO)
        TaskResponse response = TaskResponse.fromDomain(createdTask);

        // 3. Le serveur apporte le plateau au client avec un ticket (ResponseEntity)
        // Pourquoi ResponseEntity ? Pour contrôler le code HTTP.
        // Un POST qui crée quelque chose doit renvoyer "201 Created", pas "200 OK".
        // On lui donne aussi l'URL du nouvel objet créé (Best Practice REST absolue).
        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + response.id()))
                .body(response);
    }

    /*
     * @GetMapping : Réagit aux requêtes HTTP GET (lecture). Pas besoin de @RequestBody
     * car on ne lit pas de JSON envoyé par le client, on lui donne des données.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks() {
        log.info("Reçu demande de listing");

        // On récupère la liste du Domaine, et on utilise l'API Stream de Java pour
        // convertir chaque "Task" en "TaskResponse" un par un.
        List<TaskResponse> responses = taskManager.getAllTasks().stream()
                .map(TaskResponse::fromDomain) // Équivalent de : task -> TaskResponse.fromDomain(task)
                .toList();

        // Pas de .created() ici, juste .ok() qui renvoie un code 200 OK avec la liste dans le corps.
        return ResponseEntity.ok(responses);
    }
}