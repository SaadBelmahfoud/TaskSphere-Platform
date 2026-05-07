package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Notification;
import com.tasksphere.core.port.out.NotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : NotificationPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 1 : Implémentation du port NotificationPort
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE — ADAPTATEUR JPA :
 * Cet adaptateur traduit les appels du port (domaine) en opérations
 * JPA via NotificationRepository. Le domaine ne connaît PAS JPA.
 *
 * NOTE SUR actorUsername :
 * Le domaine Notification ne contient pas actorUsername, mais
 * la table notifications en a besoin. Le port accepte donc
 * un paramètre supplémentaire actorUsername dans save() qui
 * est passé à l'entité JPA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPort {

    private final NotificationRepository notificationRepository;

    @Override
    public Notification save(Notification notification, String actorUsername) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la notification '{}' pour {}",
                notification.title(), notification.recipientUsername());
        NotificationEntity entity = new NotificationEntity(notification, actorUsername);
        NotificationEntity saved = notificationRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<Notification> findByUsername(String username) {
        log.debug("ADAPTATEUR JPA : Recherche des notifications pour {}", username);
        return notificationRepository.findByTargetUsernameOrderByCreatedAtDesc(username).stream()
                .map(NotificationEntity::toDomain)
                .toList();
    }

    @Override
    public long countUnread(String username) {
        log.debug("ADAPTATEUR JPA : Comptage des notifications non lues pour {}", username);
        return notificationRepository.countByTargetUsernameAndIsReadFalse(username);
    }

    @Override
    public Optional<Notification> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche de la notification {}", id);
        return notificationRepository.findById(id)
                .map(NotificationEntity::toDomain);
    }

    @Override
    @Transactional
    public void markAsRead(String id) {
        log.debug("ADAPTATEUR JPA : Marquage comme lu de la notification {}", id);
        notificationRepository.findById(id).ifPresent(entity -> {
            entity.setIsRead(true);
            notificationRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        log.debug("ADAPTATEUR JPA : Marquage de toutes les notifications comme lues pour {}", username);
        notificationRepository.markAllAsReadByTargetUsername(username);
    }
}