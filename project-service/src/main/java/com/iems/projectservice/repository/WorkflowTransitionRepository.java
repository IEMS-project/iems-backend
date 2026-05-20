package com.iems.projectservice.repository;

import com.iems.projectservice.entity.WorkflowTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {
    List<WorkflowTransition> findByWorkflowId(UUID workflowId);
    List<WorkflowTransition> findByWorkflowIdAndFromStatusId(UUID workflowId, UUID fromStatusId);
    boolean existsByWorkflowIdAndFromStatusIdAndToStatusId(UUID workflowId, UUID fromStatusId, UUID toStatusId);
    void deleteByWorkflowId(UUID workflowId);
    void deleteByWorkflowIdIn(List<UUID> workflowIds);
}
