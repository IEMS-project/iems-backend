package com.iems.projectservice.repository;

import com.iems.projectservice.entity.IssueStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface IssueStatusHistoryRepository extends JpaRepository<IssueStatusHistory, UUID> {
    List<IssueStatusHistory> findBySprintIdAndChangedAtBetweenOrderByChangedAtAsc(
            UUID sprintId,
            LocalDateTime from,
            LocalDateTime to);

    List<IssueStatusHistory> findBySprintIdOrderByChangedAtAsc(UUID sprintId);

    List<IssueStatusHistory> findByIssueIdInOrderByChangedAtAsc(Set<UUID> issueIds);

    void deleteByProjectId(UUID projectId);
}
