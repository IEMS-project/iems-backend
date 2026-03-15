package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateWorkflowDto;
import com.iems.projectservice.dto.request.CreateWorkflowStatusDto;
import com.iems.projectservice.dto.request.CreateWorkflowTransitionDto;
import com.iems.projectservice.entity.Workflow;
import com.iems.projectservice.entity.WorkflowStatus;
import com.iems.projectservice.entity.WorkflowTransition;
import com.iems.projectservice.entity.enums.StatusCategory;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.WorkflowRepository;
import com.iems.projectservice.repository.WorkflowStatusRepository;
import com.iems.projectservice.repository.WorkflowTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;

    // --- Workflow CRUD ---
    public Workflow createWorkflow(UUID projectId, CreateWorkflowDto dto) {
        Workflow wf = new Workflow();
        wf.setProjectId(projectId);
        wf.setName(dto.getName());
        wf.setDescription(dto.getDescription());
        wf.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        return workflowRepository.save(wf);
    }

    public Workflow updateWorkflow(UUID workflowId, CreateWorkflowDto dto) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        if (dto.getName() != null)
            wf.setName(dto.getName());
        if (dto.getDescription() != null)
            wf.setDescription(dto.getDescription());
        if (dto.getIsDefault() != null)
            wf.setIsDefault(dto.getIsDefault());
        return workflowRepository.save(wf);
    }

    @Transactional
    public void deleteWorkflow(UUID workflowId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        workflowTransitionRepository.deleteByWorkflowId(workflowId);
        workflowStatusRepository.deleteByWorkflowId(workflowId);
        workflowRepository.delete(wf);
    }

    public List<Workflow> getWorkflowsByProject(UUID projectId) {
        return workflowRepository.findByProjectId(projectId);
    }

    public Workflow getWorkflowById(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
    }

    // --- Status CRUD ---
    public WorkflowStatus addStatus(UUID workflowId, CreateWorkflowStatusDto dto) {
        workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));

        List<WorkflowStatus> existing = workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
        int nextOrder = dto.getSortOrder() != null ? dto.getSortOrder() : existing.size();

        WorkflowStatus status = new WorkflowStatus();
        status.setWorkflowId(workflowId);
        status.setName(dto.getName());
        status.setCategory(dto.getCategory() != null ? dto.getCategory() : StatusCategory.TODO);
        status.setSortOrder(nextOrder);
        status.setColor(dto.getColor());
        return workflowStatusRepository.save(status);
    }

    public WorkflowStatus updateStatus(UUID statusId, CreateWorkflowStatusDto dto) {
        WorkflowStatus status = workflowStatusRepository.findById(statusId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND));
        if (dto.getName() != null)
            status.setName(dto.getName());
        if (dto.getCategory() != null)
            status.setCategory(dto.getCategory());
        if (dto.getSortOrder() != null)
            status.setSortOrder(dto.getSortOrder());
        if (dto.getColor() != null)
            status.setColor(dto.getColor());
        return workflowStatusRepository.save(status);
    }

    public void deleteStatus(UUID statusId) {
        WorkflowStatus status = workflowStatusRepository.findById(statusId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND));
        workflowStatusRepository.delete(status);
    }

    public List<WorkflowStatus> getStatuses(UUID workflowId) {
        return workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
    }

    // --- Transition CRUD ---
    public WorkflowTransition addTransition(UUID workflowId, CreateWorkflowTransitionDto dto) {
        workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));

        WorkflowTransition t = new WorkflowTransition();
        t.setWorkflowId(workflowId);
        t.setFromStatusId(dto.getFromStatusId());
        t.setToStatusId(dto.getToStatusId());
        t.setName(dto.getName());
        return workflowTransitionRepository.save(t);
    }

    public void deleteTransition(UUID transitionId) {
        WorkflowTransition t = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_TRANSITION_NOT_FOUND));
        workflowTransitionRepository.delete(t);
    }

    public List<WorkflowTransition> getTransitions(UUID workflowId) {
        return workflowTransitionRepository.findByWorkflowId(workflowId);
    }

    public boolean isValidTransition(UUID workflowId, UUID fromStatusId, UUID toStatusId) {
        return workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(
                workflowId, fromStatusId, toStatusId);
    }

    /**
     * Create a default workflow with standard statuses for a new project.
     */
    @Transactional
    public Workflow createDefaultWorkflow(UUID projectId) {
        Workflow wf = new Workflow();
        wf.setProjectId(projectId);
        wf.setName("Default Workflow");
        wf.setDescription("Default project workflow");
        wf.setIsDefault(true);
        wf = workflowRepository.save(wf);

        // Create default statuses
        WorkflowStatus todo = createStatus(wf.getId(), "To Do", StatusCategory.TODO, 0, "#6B7280");
        WorkflowStatus inProgress = createStatus(wf.getId(), "In Progress", StatusCategory.IN_PROGRESS, 1, "#3B82F6");
        WorkflowStatus review = createStatus(wf.getId(), "Review", StatusCategory.IN_PROGRESS, 2, "#F59E0B");
        WorkflowStatus done = createStatus(wf.getId(), "Done", StatusCategory.DONE, 3, "#10B981");

        // Create default transitions
        createTransitionInternal(wf.getId(), todo.getId(), inProgress.getId(), "Start Progress");
        createTransitionInternal(wf.getId(), inProgress.getId(), review.getId(), "Submit for Review");
        createTransitionInternal(wf.getId(), review.getId(), done.getId(), "Approve");
        createTransitionInternal(wf.getId(), review.getId(), inProgress.getId(), "Request Changes");
        createTransitionInternal(wf.getId(), inProgress.getId(), todo.getId(), "Stop Progress");

        return wf;
    }

    private WorkflowStatus createStatus(UUID workflowId, String name, StatusCategory category, int sortOrder,
            String color) {
        WorkflowStatus status = new WorkflowStatus();
        status.setWorkflowId(workflowId);
        status.setName(name);
        status.setCategory(category);
        status.setSortOrder(sortOrder);
        status.setColor(color);
        return workflowStatusRepository.save(status);
    }

    private void createTransitionInternal(UUID workflowId, UUID fromId, UUID toId, String name) {
        WorkflowTransition t = new WorkflowTransition();
        t.setWorkflowId(workflowId);
        t.setFromStatusId(fromId);
        t.setToStatusId(toId);
        t.setName(name);
        workflowTransitionRepository.save(t);
    }
}
