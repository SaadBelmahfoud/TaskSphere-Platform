package com.tasksphere.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<AttachmentEntity, String> {

    List<AttachmentEntity> findByTaskIdOrderByUploadedAtDesc(String taskId);
}