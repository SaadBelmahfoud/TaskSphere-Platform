package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Attachment;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
public class AttachmentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String taskId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 255)
    private String uploadedBy;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    protected AttachmentEntity() {}

    public AttachmentEntity(Attachment attachment) {
        this.id = attachment.id();
        this.taskId = attachment.taskId();
        this.fileName = attachment.fileName();
        this.contentType = attachment.contentType();
        this.fileSize = attachment.fileSize();
        this.storageKey = attachment.storageKey();
        this.uploadedBy = attachment.uploadedBy();
        this.uploadedAt = attachment.uploadedAt();
    }

    public Attachment toDomain() {
        return new Attachment(id, taskId, fileName, contentType, fileSize, storageKey, uploadedBy, uploadedAt);
    }

    public String getStorageKey() { return storageKey; }
}