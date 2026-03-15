package com.iems.projectservice.repository;

import com.iems.projectservice.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ActivityLog> findByIssueIdOrderByCreatedAtDesc(UUID issueId);
}
