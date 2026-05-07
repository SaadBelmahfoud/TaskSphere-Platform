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
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION PHASE 3 — actorUsername maintenant dans le domaine
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT : actorUsername n'était PAS dans le domaine Notification.
 * Le port save() prenait un paramètre séparé String actorUsername,
 * et le constructeur NotificationEntity(notification, actorUsername)
 * avait 2 arguments.
 *
 * APRÈS : actorUsername EST dans le domaine Notification.
 * Le constructeur NotificationEntity(notification) n'a plus qu'1 argument
 * car actorUsername est obtenu via notification.actorUsername().
 * Le paramètre actorUsername du port save() est conservé pour
 * compatibilité ascendante mais n'est plus utilisé directement
 * dans le constructeur de l'entité (il est déjà dans le domaine).
 *
 * NOTE SUR LE PARAMÈTRE actorUsername DU PORT :
 * NotificationPort.save(notification, actorUsername) conserve son
 * signature pour compatibilité. L'actorUsername passé en paramètre
 * devrait être le même que notification.actorUsername(). Dans une
 * version future, on pourrait simplifier le port en retirant ce
 * paramètre redondant.
 * ═══════════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPort {

    private final NotificationRepository notificationRepository;

    @Override
    public Notification save(Notification notification, String actorUsername) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la notification '{}' pour {} (acteur: {})",
                notification.title(), notification.recipientUsername(),
                notification.actorUsername());
        // CORRECTION PHASE 3 : Constructeur 1 arg (actorUsername est maintenant dans le domaine)
        NotificationEntity entity = new NotificationEntity(notification);
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