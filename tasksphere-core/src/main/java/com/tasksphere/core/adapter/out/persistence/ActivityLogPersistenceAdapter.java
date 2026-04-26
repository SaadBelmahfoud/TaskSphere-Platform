package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.port.out.ActivityLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : ActivityLogPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * Implémente le port ActivityLogPort.
 *
 * SIMPLICITÉ : Pas de chemin dual, pas de dirty checking.
 * L'audit log est APPEND-ONLY : save() = INSERT uniquement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogPersistenceAdapter implements ActivityLogPort {

    private final ActivityLogRepository activityLogRepository;

    @Override
    public ActivityLog save(ActivityLog activityLog) {
        log.debug("ADAPTATEUR JPA : Enregistrement activité ({}) par {}",
                activityLog.action(), activityLog.username());
        ActivityLogEntity entity = new ActivityLogEntity(activityLog);
        return activityLogRepository.save(entity).toDomain();
    }

    @Override
    public List<ActivityLog> findRecent(int limit) {
        return activityLogRepository.findRecent(limit, PageRequest.of(0, limit))
                .stream()
                .map(ActivityLogEntity::toDomain)
                .toList();
    }

    @Override
    public Page<ActivityLog> findAll(String taskId, org.springframework.data.domain.Pageable pageable) {
        return activityLogRepository.findAllWithFilter(taskId, pageable)
                .map(ActivityLogEntity::toDomain);
    }
}