package com.iems.projectservice.repository;

import com.iems.projectservice.entity.WorkflowStatus;
import com.iems.projectservice.entity.enums.StatusCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {
    List<WorkflowStatus> findByWorkflowIdOrderBySortOrderAsc(UUID workflowId);
    List<WorkflowStatus> findByWorkflowIdInAndCategory(List<UUID> workflowIds, StatusCategory category);
    void deleteByWorkflowId(UUID workflowId);
}
