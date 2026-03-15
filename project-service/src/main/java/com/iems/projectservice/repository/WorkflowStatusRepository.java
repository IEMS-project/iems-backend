package com.iems.projectservice.repository;

import com.iems.projectservice.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {
    List<WorkflowStatus> findByWorkflowIdOrderBySortOrderAsc(UUID workflowId);
    void deleteByWorkflowId(UUID workflowId);
}
