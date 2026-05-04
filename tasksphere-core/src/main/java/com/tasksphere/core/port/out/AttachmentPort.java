package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Attachment;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * PORT SORTANT : Persistance des métadonnées des pièces jointes
 * ═══════════════════════════════════════════════════════════════════
 */
public interface AttachmentPort {

    Attachment save(Attachment attachment);

    List<Attachment> findByTaskId(String taskId);

    Optional<Attachment> findById(String id);

    void deleteById(String id);
}