package com.tasksphere.core.service;

import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.dto.NotificationResponse;
import com.tasksphere.core.port.out.NotificationPort;
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
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — Persistance AVANT push WebSocket
 * ═══════════════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 * Les notifications étaient uniquement pushées via WebSocket (éphémères).
 * Si l'utilisateur était déconnecté, la notification était PERDUE.
 * Les endpoints REST (/notifications, /notifications/unread-count)
 * ne retournaient rien car rien n'était persisté en base.
 *
 * SOLUTION :
 * 1. Injecter NotificationPort pour la persistance
 * 2. Persisté la notification AVANT le push WebSocket
 * 3. Ajouter le paramètre actorUsername pour savoir qui a déclenché l'action
 *
 * PRINCIPE — PERSIST AVANT PUSH :
 * On persiste d'abord en base, puis on pousse via WebSocket.
 * Si le push échoue (utilisateur déconnecté), la notification
 * est quand même en base et pourra être récupérée via REST.
 * L'ordre inverse (push puis persist) risquerait de perdre la
 * notification si le persist échouait après un push réussi.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * ═══════════════════════════════════════════════════════════════════
     * CORRECTION PHASE 3 : Injection du NotificationPort
     * ═══════════════════════════════════════════════════════════════════
     * NotificationPort permet de persister les notifications en base
     * AVANT de les pusher via WebSocket. Cela garantit que les
     * notifications ne sont pas perdues si l'utilisateur est
     * déconnecté, et permet aux endpoints REST de fonctionner.
     * ═══════════════════════════════════════════════════════════════════
     */
    private final NotificationPort notificationPort;

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
     * CORRECTION PHASE 3 : Persistance AVANT push WebSocket.
     * La notification contient maintenant actorUsername (dans le domaine).
     *
     * @param recipientUsername L'email du destinataire
     * @param notification      La notification à envoyer (contient actorUsername)
     */
    public void notifyUser(String recipientUsername, Notification notification) {
        log.info("NOTIFICATION : Envoi à {} — {} : {}",
                recipientUsername, notification.type(), notification.title());

        try {
            // ═══════════════════════════════════════════════════════════════════
            // CORRECTION PHASE 3 : Persister AVANT le push WebSocket
            // ═══════════════════════════════════════════════════════════════════
            // On sauvegarde d'abord en base. Ainsi, même si le WebSocket
            // échoue (utilisateur déconnecté), la notification sera
            // récupérable via GET /api/v1/notifications.
            // actorUsername est obtenu depuis notification.actorUsername().
            // ═══════════════════════════════════════════════════════════════════
            notificationPort.save(notification, notification.actorUsername());

            // Ensuite, pousser via WebSocket
            NotificationResponse response = NotificationResponse.fromDomain(notification);
            messagingTemplate.convertAndSendToUser(
                    recipientUsername,
                    "/queue/notifications",
                    response
            );
        } catch (Exception e) {
            // Le WebSocket peut échouer si l'utilisateur n'est pas connecté
            // Ce n'est PAS critique : la notification est déjà persistée
            // et sera récupérable via l'API REST.
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
     *
     * CORRECTION PHASE 3 : Persister les broadcasts aussi
     * Les broadcasts sont persistés avec actorUsername = null (événement système).
     */
    public void broadcast(Notification notification) {
        log.info("NOTIFICATION : Broadcast — {} : {}", notification.type(), notification.title());

        try {
            // Persister avec actorUsername = null (système)
            notificationPort.save(notification, null);

            NotificationResponse response = NotificationResponse.fromDomain(notification);
            messagingTemplate.convertAndSend("/topic/notifications", response);
        } catch (Exception e) {
            log.warn("NOTIFICATION : Échec du broadcast — {}", e.getMessage());
        }
    }
}