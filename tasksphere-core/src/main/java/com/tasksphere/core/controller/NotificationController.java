package com.tasksphere.core.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : NotificationController (API REST Notifications)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : API REST complémentaire au WebSocket
 * ─────────────────────────────────────────────────────────────────────
 *
 * POURQUOI UN CONTROLLER REST EN PLUS DU WEBSOCKET ?
 * ────────────────────────────────────────────────────
 * 1. HISTORIQUE : Le WebSocket ne garde PAS les messages si le client
 *    était déconnecté. Le REST permet de récupérer les notifications
 *    manquées lors de la reconnexion.
 * 2. PERSISTANCE : Les notifications sont stockées temporairement
 *    pour les utilisateurs hors ligne.
 * 3. ACTIONS : Marquer comme lu, supprimer, etc.
 *
 * DANS CETTE VERSION SIMPLIFIÉE :
 * ────────────────────────────────
 * Les notifications sont uniquement pushées via WebSocket (éphémères).
 * Le controller REST fournit un endpoint de santé pour vérifier que
 * le canal WebSocket est fonctionnel.
 *
 * DANS UNE VERSION FUTURE :
 * ──────────────────────────
 * - GET /api/v1/notifications → Liste des notifications non lues (stockées en BDD)
 * - PATCH /api/v1/notifications/{id}/read → Marquer comme lu
 * - DELETE /api/v1/notifications/{id} → Supprimer
 *
 * ENDPOINTS :
 * ──────────
 * GET /api/v1/notifications/health → Vérifie que le service WebSocket est actif
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /**
     * GET /api/v1/notifications/health
     *
     * Endpoint de santé pour vérifier que le service de notifications
     * est actif. Utile pour le monitoring et le debugging.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health(Authentication authentication) {
        String username = authentication.getName();
        log.info("CONTROLLER : GET /notifications/health — {}", username);

        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "websocketEndpoint", "/ws",
                "subscribeDestination", "/user/queue/notifications",
                "username", username
        ));
    }
}