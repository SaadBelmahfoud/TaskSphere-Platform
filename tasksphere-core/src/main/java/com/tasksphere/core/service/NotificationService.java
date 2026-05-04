package com.tasksphere.core.service;

import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * ═══════════════════════════════════════════════════════════════════
 * SERVICE : NotificationService (Notifications temps réel)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Service de push de notifications temps réel
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE — SIMPMESSAGINGTEMPLATE :
 * ────────────────────────────────────
 * SimpMessagingTemplate est la classe Spring qui permet d'envoyer
 * des messages STOMP vers les clients WebSocket connectés.
 *
 * MÉTHODES CLÉS :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  convertAndSend(destination, payload)                            │
 * │  → Envoie à TOUS les abonnés de la destination                  │
 * │  → Exemple : /topic/notifications → broadcast global            │
 * │                                                                  │
 * │  convertAndSendToUser(username, destination, payload)            │
 * │  → Envoie à UN SEUL utilisateur identifié par username           │
 * │  → Exemple : /queue/notifications → canal privé                 │
 * │  → Spring route automatiquement vers la bonne session WebSocket │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * FLUX DE NOTIFICATION :
 * ───────────────────────
 * 1. Un événement se produit (TaskAuditEvent, UserAdminEvent)
 * 2. Le listener appelle notificationService.notifyUser(...)
 * 3. NotificationService crée une Notification
 * 4. Il envoie via SimpMessagingTemplate.convertAndSendToUser()
 * 5. Le frontend (abonné à /user/queue/notifications) reçoit le message
 * 6. Le frontend affiche un toast/badge
 *
 * POURQUOI UN SERVICE ET PAS UN APPEL DIRECT AU TEMPLATE ?
 * ─────────────────────────────────────────────────────────
 * 1. SRP : Le service encapsule la logique de création de notification
 * 2. Testabilité : On peut mocker ce service dans les tests
 * 3. Extensibilité : On pourra ajouter du stockage, des préférences, etc.
 * 4. Cohérence : Toutes les notifications passent par le même point
 *
 * NOTE SUR LE USERNAME :
 * ────────────────────────
 * convertAndSendToUser() utilise le "username" Spring Security.
 * Dans notre cas, c'est l'email de l'utilisateur (authentication.getName()).
 * Le frontend doit se connecter au WebSocket avec le même principal
 * pour que Spring puisse router les messages correctement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Envoie une notification à un utilisateur spécifique via WebSocket.
     *
     * PRINCIPE DE ROUTAGE :
     * ──────────────────────
     * convertAndSendToUser(username, "/queue/notifications", payload)
     * → Spring ajoute le préfixe /user/ automatiquement
     * → Le client s'abonne à /user/queue/notifications
     * → Spring route vers la session WebSocket de cet utilisateur
     *
     * @param recipientUsername L'email du destinataire
     * @param notification      La notification à envoyer
     */
    public void notifyUser(String recipientUsername, Notification notification) {
        log.info("NOTIFICATION : Envoi à {} — {} : {}",
                recipientUsername, notification.type(), notification.title());

        try {
            NotificationResponse response = NotificationResponse.fromDomain(notification);
            messagingTemplate.convertAndSendToUser(
                    recipientUsername,
                    "/queue/notifications",
                    response
            );
        } catch (Exception e) {
            // Le WebSocket peut échouer si l'utilisateur n'est pas connecté
            // Ce n'est PAS critique : la notification sera simplement perdue
            // (dans une version future, on stockerait les notifications non lues)
            log.warn("NOTIFICATION : Échec de l'envoi WebSocket à {} — {}", recipientUsername, e.getMessage());
        }
    }

    /**
     * Envoie une notification broadcast à tous les utilisateurs connectés.
     *
     * UTILISATION : Notifications globales (maintenance, annonce, etc.)
     *
     * PRINCIPE :
     * convertAndSend("/topic/notifications", payload)
     * → Tous les clients abonnés à /topic/notifications reçoivent le message
     */
    public void broadcast(Notification notification) {
        log.info("NOTIFICATION : Broadcast — {} : {}", notification.type(), notification.title());

        try {
            NotificationResponse response = NotificationResponse.fromDomain(notification);
            messagingTemplate.convertAndSend("/topic/notifications", response);
        } catch (Exception e) {
            log.warn("NOTIFICATION : Échec du broadcast — {}", e.getMessage());
        }
    }
}