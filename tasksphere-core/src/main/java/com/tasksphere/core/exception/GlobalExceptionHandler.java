package com.tasksphere.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/*
 * ====================================================================
 * GESTIONNAIRE GLOBAL DES EXCEPTIONS (Centralized Exception Handler)
 * ====================================================================
 *
 * PRINCIPE @RestControllerAdvice :
 * C'est un intercepteur global qui s'applique à TOUS les @RestController.
 * Il intercepte les exceptions lancées par les contrôleurs et retourne
 * une réponse HTTP structurée au lieu d'une stack trace illisible.
 *
 * PRINCIPE @ExceptionHandler :
 * Chaque méthode annotée gère un type d'exception spécifique.
 * Spring appelle automatiquement la bonne méthode selon le type de l'exception.
 *
 * SANS ce handler :
 *   Quand @Valid échoue → Spring retourne 500 avec une stack trace :
 *   {
 *     "timestamp": "2024-01-15T10:30:00.000+00:00",
 *     "status": 500,
 *     "error": "Internal Server Error",
 *     "path": "/api/v1/tasks"
 *   }
 *   → Pas de détail sur l'erreur, pas professionnel.
 *
 * AVEC ce handler :
 *   Quand @Valid échoue → Spring retourne 400 avec un détail clair :
 *   {
 *     "timestamp": "2024-01-15T10:30:00.000+00:00",
 *     "status": 400,
 *     "error": "Bad Request",
 *     "message": "Erreur de validation",
 *     "details": {
 *       "title": "Le titre est obligatoire",
 *       "description": "La description ne peut pas dépasser 5000 caractères"
 *     },
 *     "path": "/api/v1/tasks"
 *   }
 *   → Claire, exploitable par le frontend, professionnel.
 *
 * PRINCIPE D'ORDRE DE PRIORITÉ :
 * Spring choisit le handler le plus spécifique en premier.
 * Si une exception correspond à plusieurs handlers, le plus précis gagne.
 * L'ordre recommandé :
 * 1. Exceptions spécifiques (MethodArgumentNotValid, IllegalArgumentException)
 * 2. Exception générique (catch-all pour les erreurs inattendues)
 *
 * PRINCIPE DE FORMAT DE RÉPONSE :
 * On utilise un format cohérent pour TOUTES les erreurs :
 * {
 *   "timestamp": "...",      // Date de l'erreur
 *   "status": 400,           // Code HTTP
 *   "error": "Bad Request",  // Nom du statut HTTP
 *   "message": "...",        // Message lisible par l'humain
 *   "details": {...},        // Détails spécifiques (optionnel)
 *   "path": "/api/v1/tasks"  // Endpoint qui a causé l'erreur
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation Jakarta Bean Validation (@Valid).
     *
     * DÉCLENCHÉE PAR :
     * - @NotBlank sur un champ vide dans TaskCreateRequest
     * - @Size(min=3) quand le titre fait moins de 3 caractères
     * - Toute annotation Jakarta Validation sur un @Valid @RequestBody
     *
     * PRINCIPE MethodArgumentNotValidException :
     * Cette exception contient la liste de TOUTES les erreurs de validation,
     * pas seulement la première. Cela permet au client de corriger toutes
     * les erreurs en une seule fois.
     *
     * FLUX :
     * Client envoie JSON invalide → Controller reçoit @Valid → Validation échoue
     * → MethodArgumentNotValidException → CETTE MÉTHODE → 400 Bad Request
     *
     * @param ex  L'exception contenant les détails de validation
     * @return    ResponseEntity avec les détails des erreurs
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Extraire les erreurs champ par champ
        Map<String, String> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("VALIDATION ERROR : {} erreurs sur le champ(s) {}", details.size(), details.keySet());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Erreur de validation",
                details
        );
    }

    /**
     * Gère les arguments illégaux (ex: enum invalide).
     *
     * DÉCLENCHÉE PAR :
     * - Task.TaskPriority.valueOf("INVALID") → IllegalArgumentException
     * - Toute validation métier qui lance une IllegalArgumentException
     *
     * NOTE : Actuellement, le contrôleur valide manuellement les enums
     * avant d'appeler le service (voir TaskController.updateTaskStatus).
     * Ce handler est un filet de sécurité pour les cas non gérés.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("ILLEGAL ARGUMENT : {}", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                null
        );
    }

    /**
     * Filet de sécurité : gère TOUTES les exceptions non interceptées.
     *
     * PRINCIPE DU CATCH-ALL :
     * C'est le dernier recours. Si aucune autre méthode @ExceptionHandler
     * ne correspond, celle-ci est appelée.
     *
     * PRINCIPE DE SÉCURITÉ :
     * On NE LOGUE PAS les détails de l'exception dans la réponse HTTP
     * (pas de ex.getMessage() ni de stack trace). Pourquoi ?
     * - Une exception inattendue peut contenir des infos sensibles (SQL, paths système)
     * - Ces infos pourraient aider un attaquant à comprendre l'architecture
     * - On retourne un message générique et on loggue les détails côté serveur
     *
     * EN PRODUCTION : La réponse ne doit JAMAIS révéler de détails techniques.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        // Log détaillé côté serveur (pour le debugging)
        log.error("UNEXPECTED ERROR : {}", ex.getMessage(), ex);

        // Réponse générique côté client (pas de détails techniques)
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur interne est survenue. Veuillez réessayer plus tard.",
                null
        );
    }

    /**
     * Construit une réponse d'erreur structurée et cohérente.
     *
     * PRINCIPE DE FORMAT UNIFORME :
     * Toutes les erreurs ont le même format JSON. Cela permet au frontend
     * de parser les erreurs de manière uniforme, peu importe le type d'erreur.
     *
     * FORMAT :
     * {
     *   "timestamp": "2024-01-15T10:30:00",
     *   "status": 400,
     *   "error": "Bad Request",
     *   "message": "Erreur de validation",
     *   "details": { "title": "Le titre est obligatoire" },
     *   "path": "/api/v1/tasks"
     * }
     *
     * @param status   Code HTTP (400, 404, 500...)
     * @param message  Message lisible par l'humain
     * @param details  Détails spécifiques (optionnel, peut être null)
     * @return         ResponseEntity avec le corps d'erreur structuré
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String message,
            Map<String, String> details) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        body.put("path", "");

        return ResponseEntity.status(status).body(body);
    }
}