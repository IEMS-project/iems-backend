package com.iems.projectservice.repository;

import com.iems.projectservice.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    Page<ActivityLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<ActivityLog> findByProjectIdInOrderByCreatedAtDesc(Collection<UUID> projectIds, Pageable pageable);

    Page<ActivityLog> findByProjectIdInAndUserIdOrderByCreatedAtDesc(Collection<UUID> projectIds, UUID userId, Pageable pageable);

    Page<ActivityLog> findByIssueIdOrderByCreatedAtDesc(UUID issueId, Pageable pageable);

    void deleteByProjectId(UUID projectId);
}
