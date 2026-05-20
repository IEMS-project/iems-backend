package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByIssueId(UUID issueId);
    void deleteByIssueId(UUID issueId);
    void deleteByIssueIdIn(List<UUID> issueIds);
}
