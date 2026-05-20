package com.iems.projectservice.repository;

import com.iems.projectservice.entity.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {
    List<Issue> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    Page<Issue> findByProjectId(UUID projectId, Pageable pageable);

    List<Issue> findByProjectIdAndSprintIdIsNullOrderBySortOrderAsc(UUID projectId);

    List<Issue> findBySprintIdOrderBySortOrderAsc(UUID sprintId);

    List<Issue> findByParentId(UUID parentId);

    List<Issue> findByProjectIdAndStatusId(UUID projectId, UUID statusId);

    List<Issue> findByAssigneeId(UUID assigneeId);

    Optional<Issue> findByProjectIdAndIssueKey(UUID projectId, String issueKey);

    @Query("SELECT MAX(i.sortOrder) FROM Issue i WHERE i.projectId = :projectId")
    Optional<Integer> findMaxSortOrderByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT COUNT(i) FROM Issue i WHERE i.projectId = :projectId")
    long countByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT i.id FROM Issue i WHERE i.projectId = :projectId")
    List<UUID> findIdsByProjectId(@Param("projectId") UUID projectId);

    void deleteByProjectId(UUID projectId);

    @Modifying
    @Query(value = "DELETE FROM issue_labels WHERE issue_id IN (SELECT id FROM issues WHERE project_id = :projectId)",
            nativeQuery = true)
    void deleteIssueLabelsByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT i FROM Issue i WHERE i.sprintId = :sprintId AND i.statusId NOT IN " +
            "(SELECT ws.id FROM WorkflowStatus ws WHERE ws.workflowId IN " +
            "(SELECT w.id FROM Workflow w WHERE w.projectId = :projectId) AND ws.category = 'DONE')")
    List<Issue> findIncompleteIssuesInSprint(@Param("sprintId") UUID sprintId, @Param("projectId") UUID projectId);

    /**
     * Tìm tất cả issues có dueDate = ngày chỉ định VÀ có assignee (dùng cho due-date reminder).
     */
    List<Issue> findByDueDateAndAssigneeIdNotNull(LocalDate dueDate);
}
