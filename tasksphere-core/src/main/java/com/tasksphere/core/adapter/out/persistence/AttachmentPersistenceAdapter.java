package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Attachment;
import com.tasksphere.core.port.out.AttachmentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentPersistenceAdapter implements AttachmentPort {

    private final AttachmentRepository attachmentRepository;

    @Override
    public Attachment save(Attachment attachment) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la pièce jointe '{}' pour la tâche {}",
                attachment.fileName(), attachment.taskId());
        AttachmentEntity entity = new AttachmentEntity(attachment);
        AttachmentEntity saved = attachmentRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<Attachment> findByTaskId(String taskId) {
        return attachmentRepository.findByTaskIdOrderByUploadedAtDesc(taskId).stream()
                .map(AttachmentEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Attachment> findById(String id) {
        return attachmentRepository.findById(id)
                .map(AttachmentEntity::toDomain);
    }

    @Override
    public void deleteById(String id) {
        attachmentRepository.deleteById(id);
    }
}