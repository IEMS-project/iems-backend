package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateWorkflowDto;
import com.iems.projectservice.dto.request.CreateWorkflowStatusDto;
import com.iems.projectservice.dto.request.CreateWorkflowTransitionDto;
import com.iems.projectservice.dto.request.UpdateWorkflowDto;
import com.iems.projectservice.dto.request.UpdateWorkflowStatusDto;
import com.iems.projectservice.dto.request.WorkflowStatusSyncItemDto;
import com.iems.projectservice.entity.Workflow;
import com.iems.projectservice.entity.WorkflowStatus;
import com.iems.projectservice.entity.WorkflowTransition;
import com.iems.projectservice.entity.enums.StatusCategory;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.WorkflowRepository;
import com.iems.projectservice.repository.WorkflowStatusRepository;
import com.iems.projectservice.repository.WorkflowTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final ProjectRepository projectRepository;
    private final SubscriptionLimitService subscriptionLimitService;
    private final ActivityLogService activityLogService;

    public Workflow createWorkflow(UUID projectId, CreateWorkflowDto dto, UUID userId) {
        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        Workflow wf = new Workflow();
        wf.setProjectId(projectId);
        wf.setName(dto.getName());
        wf.setDescription(dto.getDescription());
        wf.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        Workflow saved = workflowRepository.save(wf);
        activityLogService.log(projectId, null, userId, "WORKFLOW_CREATED",
                "Created workflow: " + saved.getName());
        return saved;
    }

    public Workflow updateWorkflow(UUID workflowId, UpdateWorkflowDto dto, UUID userId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        List<String> changedFields = new ArrayList<>();
        if (dto.getName() != null && !Objects.equals(wf.getName(), dto.getName())) {
            wf.setName(dto.getName());
            changedFields.add("name");
        }
        if (dto.getDescription() != null && !Objects.equals(wf.getDescription(), dto.getDescription())) {
            wf.setDescription(dto.getDescription());
            changedFields.add("description");
        }
        if (dto.getIsDefault() != null && !Objects.equals(wf.getIsDefault(), dto.getIsDefault())) {
            wf.setIsDefault(dto.getIsDefault());
            changedFields.add("default flag");
        }

        Workflow saved = workflowRepository.save(wf);
        if (!changedFields.isEmpty()) {
            activityLogService.log(saved.getProjectId(), null, userId, "WORKFLOW_UPDATED",
                    "Updated workflow " + saved.getName() + ": " + String.join(", ", changedFields));
        }
        return saved;
    }

    @Transactional
    public void deleteWorkflow(UUID workflowId, UUID userId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        workflowTransitionRepository.deleteByWorkflowId(workflowId);
        workflowStatusRepository.deleteByWorkflowId(workflowId);
        activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_DELETED",
                "Deleted workflow: " + wf.getName());
        workflowRepository.delete(wf);
    }

    public List<Workflow> getWorkflowsByProject(UUID projectId) {
        return workflowRepository.findByProjectId(projectId);
    }

    public Workflow getWorkflowById(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
    }

    public WorkflowStatus addStatus(UUID workflowId, CreateWorkflowStatusDto dto, UUID userId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        List<WorkflowStatus> existing = workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
        int nextOrder = dto.getSortOrder() != null ? dto.getSortOrder() : existing.size();

        WorkflowStatus status = new WorkflowStatus();
        status.setWorkflowId(workflowId);
        status.setName(dto.getName());
        status.setCategory(dto.getCategory() != null ? dto.getCategory() : StatusCategory.TODO);
        status.setSortOrder(nextOrder);
        status.setColor(dto.getColor());
        WorkflowStatus saved = workflowStatusRepository.save(status);
        activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_STATUS_CREATED",
                "Added status " + saved.getName() + " to workflow " + wf.getName());
        return saved;
    }

    public WorkflowStatus updateStatus(UUID statusId, UpdateWorkflowStatusDto dto, UUID userId) {
        WorkflowStatus status = workflowStatusRepository.findById(statusId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND));
        Workflow wf = workflowRepository.findById(status.getWorkflowId())
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        String oldName = status.getName();
        List<String> changedFields = new ArrayList<>();
        if (dto.getName() != null && !Objects.equals(status.getName(), dto.getName())) {
            status.setName(dto.getName());
            changedFields.add("name");
        }
        if (dto.getCategory() != null && !Objects.equals(status.getCategory(), dto.getCategory())) {
            status.setCategory(dto.getCategory());
            changedFields.add("category");
        }
        if (dto.getSortOrder() != null && !Objects.equals(status.getSortOrder(), dto.getSortOrder())) {
            status.setSortOrder(dto.getSortOrder());
            changedFields.add("sort order");
        }
        if (dto.getColor() != null && !Objects.equals(status.getColor(), dto.getColor())) {
            status.setColor(dto.getColor());
            changedFields.add("color");
        }

        WorkflowStatus saved = workflowStatusRepository.save(status);
        if (!changedFields.isEmpty()) {
            activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_STATUS_UPDATED",
                    "Updated status " + oldName + " in workflow " + wf.getName() + ": "
                            + String.join(", ", changedFields));
        }
        return saved;
    }

    public void deleteStatus(UUID statusId, UUID userId) {
        WorkflowStatus status = workflowStatusRepository.findById(statusId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND));
        Workflow wf = workflowRepository.findById(status.getWorkflowId())
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_STATUS_DELETED",
                "Deleted status " + status.getName() + " from workflow " + wf.getName());
        workflowStatusRepository.delete(status);
    }

    public List<WorkflowStatus> getStatuses(UUID workflowId) {
        return workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
    }

    @Transactional
    public List<WorkflowStatus> syncStatuses(UUID workflowId, List<WorkflowStatusSyncItemDto> items, UUID userId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        List<WorkflowStatusSyncItemDto> safeItems = items != null ? items : List.of();
        List<WorkflowStatus> existing = workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
        Map<UUID, WorkflowStatus> existingById = new HashMap<>();
        for (WorkflowStatus status : existing) {
            existingById.put(status.getId(), status);
        }

        int sortOrder = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        for (WorkflowStatusSyncItemDto item : safeItems) {
            if (item == null) {
                continue;
            }
            boolean removed = Boolean.TRUE.equals(item.getRemoved());

            if (removed && item.getId() != null) {
                WorkflowStatus status = existingById.get(item.getId());
                if (status == null || !status.getWorkflowId().equals(workflowId)) {
                    throw new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND);
                }
                workflowStatusRepository.delete(status);
                deletedCount++;
                continue;
            }

            if (item.getId() != null) {
                WorkflowStatus status = existingById.get(item.getId());
                if (status == null || !status.getWorkflowId().equals(workflowId)) {
                    throw new AppException(ProjectErrorCode.WORKFLOW_STATUS_NOT_FOUND);
                }

                boolean changed = false;
                if (item.getName() != null && !item.getName().trim().isEmpty()) {
                    String nextName = item.getName().trim();
                    changed = changed || !Objects.equals(status.getName(), nextName);
                    status.setName(nextName);
                }
                if (item.getColor() != null) {
                    changed = changed || !Objects.equals(status.getColor(), item.getColor());
                    status.setColor(item.getColor());
                }
                if (item.getCategory() != null) {
                    changed = changed || !Objects.equals(status.getCategory(), item.getCategory());
                    status.setCategory(item.getCategory());
                }
                changed = changed || !Objects.equals(status.getSortOrder(), sortOrder);
                status.setSortOrder(sortOrder++);
                workflowStatusRepository.save(status);
                if (changed) {
                    updatedCount++;
                }
                continue;
            }

            if (item.getName() == null || item.getName().trim().isEmpty()) {
                continue;
            }

            WorkflowStatus created = new WorkflowStatus();
            created.setWorkflowId(workflowId);
            created.setName(item.getName().trim());
            created.setColor(item.getColor());
            created.setCategory(item.getCategory() != null ? item.getCategory() : StatusCategory.TODO);
            created.setSortOrder(sortOrder++);
            workflowStatusRepository.save(created);
            createdCount++;
        }

        if (createdCount > 0 || updatedCount > 0 || deletedCount > 0) {
            activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_STATUSES_SYNCED",
                    "Synced statuses in workflow " + wf.getName() + ": "
                            + createdCount + " created, " + updatedCount + " updated, " + deletedCount + " deleted");
        }

        return workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId);
    }

    public WorkflowTransition addTransition(UUID workflowId, CreateWorkflowTransitionDto dto, UUID userId) {
        Workflow wf = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        WorkflowTransition transition = new WorkflowTransition();
        transition.setWorkflowId(workflowId);
        transition.setFromStatusId(dto.getFromStatusId());
        transition.setToStatusId(dto.getToStatusId());
        transition.setName(dto.getName());
        WorkflowTransition saved = workflowTransitionRepository.save(transition);
        activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_TRANSITION_CREATED",
                "Added transition " + transitionName(saved) + " to workflow " + wf.getName());
        return saved;
    }

    public void deleteTransition(UUID transitionId, UUID userId) {
        WorkflowTransition transition = workflowTransitionRepository.findById(transitionId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_TRANSITION_NOT_FOUND));
        Workflow wf = workflowRepository.findById(transition.getWorkflowId())
                .orElseThrow(() -> new AppException(ProjectErrorCode.WORKFLOW_NOT_FOUND));
        String ownerSub = projectRepository.findById(wf.getProjectId())
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkCanModifyWorkflow(ownerSub);

        activityLogService.log(wf.getProjectId(), null, userId, "WORKFLOW_TRANSITION_DELETED",
                "Deleted transition " + transitionName(transition) + " from workflow " + wf.getName());
        workflowTransitionRepository.delete(transition);
    }

    public List<WorkflowTransition> getTransitions(UUID workflowId) {
        return workflowTransitionRepository.findByWorkflowId(workflowId);
    }

    public boolean isValidTransition(UUID workflowId, UUID fromStatusId, UUID toStatusId) {
        return workflowTransitionRepository.existsByWorkflowIdAndFromStatusIdAndToStatusId(
                workflowId, fromStatusId, toStatusId);
    }

    @Transactional
    public Workflow createDefaultWorkflow(UUID projectId) {
        Workflow wf = new Workflow();
        wf.setProjectId(projectId);
        wf.setName("Default Workflow");
        wf.setDescription("Default project workflow");
        wf.setIsDefault(true);
        wf = workflowRepository.save(wf);

        WorkflowStatus todo = createStatus(wf.getId(), "To Do", StatusCategory.TODO, 0, "#6B7280");
        WorkflowStatus inProgress = createStatus(wf.getId(), "In Progress", StatusCategory.IN_PROGRESS, 1, "#3B82F6");
        WorkflowStatus review = createStatus(wf.getId(), "Review", StatusCategory.IN_PROGRESS, 2, "#F59E0B");
        WorkflowStatus done = createStatus(wf.getId(), "Done", StatusCategory.DONE, 3, "#10B981");

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
        WorkflowTransition transition = new WorkflowTransition();
        transition.setWorkflowId(workflowId);
        transition.setFromStatusId(fromId);
        transition.setToStatusId(toId);
        transition.setName(name);
        workflowTransitionRepository.save(transition);
    }

    private String transitionName(WorkflowTransition transition) {
        if (transition.getName() != null && !transition.getName().isBlank()) {
            return transition.getName();
        }
        return transition.getFromStatusId() + " -> " + transition.getToStatusId();
    }
}
