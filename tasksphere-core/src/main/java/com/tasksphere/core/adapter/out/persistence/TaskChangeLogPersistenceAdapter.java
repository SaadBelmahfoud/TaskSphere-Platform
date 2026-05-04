package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.TaskChangeLog;
import com.tasksphere.core.port.out.TaskChangeLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR DE PERSISTANCE : TaskChangeLogPersistenceAdapter
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 2 : Implémentation du port TaskChangeLogPort
 * ─────────────────────────────────────────────────────────────────────
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * Cet adaptateur implémente le port défini par le domaine.
 * Il traduit les appels du domaine en opérations JPA concrètes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskChangeLogPersistenceAdapter implements TaskChangeLogPort {

    private final TaskChangeLogRepository changeLogRepository;

    @Override
    public TaskChangeLog save(TaskChangeLog changeLog) {
        log.debug("ADAPTATEUR JPA : Sauvegarde du changement '{}' pour la tâche {}",
                changeLog.fieldName(), changeLog.taskId());
        TaskChangeLogEntity entity = new TaskChangeLogEntity(changeLog);
        TaskChangeLogEntity saved = changeLogRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void saveAll(List<TaskChangeLog> changeLogs) {
        log.debug("ADAPTATEUR JPA : Sauvegarde en batch de {} changements", changeLogs.size());
        List<TaskChangeLogEntity> entities = changeLogs.stream()
                .map(TaskChangeLogEntity::new)
                .toList();
        changeLogRepository.saveAll(entities);
    }

    @Override
    public Page<TaskChangeLog> findByTaskId(String taskId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des changements pour la tâche {}", taskId);
        return changeLogRepository.findByTaskIdOrderByChangedAtDesc(taskId, pageable)
                .map(TaskChangeLogEntity::toDomain);
    }
}