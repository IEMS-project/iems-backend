package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.BatchWorkflowStatusSyncRequest;
import com.iems.projectservice.dto.request.CreateWorkflowDto;
import com.iems.projectservice.dto.request.CreateWorkflowStatusDto;
import com.iems.projectservice.dto.request.CreateWorkflowTransitionDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.Workflow;
import com.iems.projectservice.entity.WorkflowStatus;
import com.iems.projectservice.entity.WorkflowTransition;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/workflows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflow")
public class WorkflowController {

    private final WorkflowService workflowService;

    // --- Workflow CRUD ---
    @PostMapping
    @Operation(summary = "Create workflow")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_CREATE)
    public ResponseEntity<ApiResponseDto<Workflow>> createWorkflow(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateWorkflowDto dto) throws AppException {
        Workflow wf = workflowService.createWorkflow(projectId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Workflow created successfully", wf));
    }

    @PatchMapping("/{workflowId}")
    @Operation(summary = "Update workflow")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<Workflow>> updateWorkflow(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @Valid @RequestBody CreateWorkflowDto dto) throws AppException {
        Workflow wf = workflowService.updateWorkflow(workflowId, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Workflow updated successfully", wf));
    }

    @DeleteMapping("/{workflowId}")
    @Operation(summary = "Delete workflow")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteWorkflow(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId) throws AppException {
        workflowService.deleteWorkflow(workflowId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Workflow deleted successfully", null));
    }

    @GetMapping
    @Operation(summary = "Get project workflows")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_READ)
    public ResponseEntity<ApiResponseDto<List<Workflow>>> getWorkflows(@PathVariable UUID projectId) throws AppException {
        List<Workflow> workflows = workflowService.getWorkflowsByProject(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Workflows retrieved successfully", workflows));
    }

    @GetMapping("/{workflowId}")
    @Operation(summary = "Get workflow by ID")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_READ)
    public ResponseEntity<ApiResponseDto<Workflow>> getWorkflow(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId) throws AppException {
        Workflow wf = workflowService.getWorkflowById(workflowId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Workflow retrieved successfully", wf));
    }

    // --- Status CRUD ---
    @PostMapping("/{workflowId}/statuses")
    @Operation(summary = "Add status to workflow")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<WorkflowStatus>> addStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @Valid @RequestBody CreateWorkflowStatusDto dto) throws AppException {
        WorkflowStatus status = workflowService.addStatus(workflowId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Status added successfully", status));
    }

    @PatchMapping("/{workflowId}/statuses/{statusId}")
    @Operation(summary = "Update workflow status")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<WorkflowStatus>> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @PathVariable UUID statusId,
            @Valid @RequestBody CreateWorkflowStatusDto dto) throws AppException {
        WorkflowStatus status = workflowService.updateStatus(statusId, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Status updated successfully", status));
    }

    @DeleteMapping("/{workflowId}/statuses/{statusId}")
    @Operation(summary = "Delete workflow status")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> deleteStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @PathVariable UUID statusId) throws AppException {
        workflowService.deleteStatus(statusId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Status deleted successfully", null));
    }

    @GetMapping("/{workflowId}/statuses")
    @Operation(summary = "Get workflow statuses")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_READ)
    public ResponseEntity<ApiResponseDto<List<WorkflowStatus>>> getStatuses(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId) throws AppException {
        List<WorkflowStatus> statuses = workflowService.getStatuses(workflowId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Statuses retrieved successfully", statuses));
    }

    @PostMapping("/{workflowId}/statuses/sync")
    @Operation(summary = "Sync workflow statuses in one request")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<List<WorkflowStatus>>> syncStatuses(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @RequestBody BatchWorkflowStatusSyncRequest request) throws AppException {
        List<WorkflowStatus> statuses = workflowService.syncStatuses(workflowId, request.getStatuses());
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Statuses synced successfully", statuses));
    }

    // --- Transition CRUD ---
    @PostMapping("/{workflowId}/transitions")
    @Operation(summary = "Add transition to workflow")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<WorkflowTransition>> addTransition(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @Valid @RequestBody CreateWorkflowTransitionDto dto) throws AppException {
        WorkflowTransition t = workflowService.addTransition(workflowId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Transition added successfully", t));
    }

    @DeleteMapping("/{workflowId}/transitions/{transitionId}")
    @Operation(summary = "Delete transition")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> deleteTransition(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId,
            @PathVariable UUID transitionId) throws AppException {
        workflowService.deleteTransition(transitionId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Transition deleted successfully", null));
    }

    @GetMapping("/{workflowId}/transitions")
    @Operation(summary = "Get workflow transitions")
    @RequireProjectPermission(ProjectPermission.WORKFLOW_READ)
    public ResponseEntity<ApiResponseDto<List<WorkflowTransition>>> getTransitions(
            @PathVariable UUID projectId,
            @PathVariable UUID workflowId) throws AppException {
        List<WorkflowTransition> transitions = workflowService.getTransitions(workflowId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Transitions retrieved successfully", transitions));
    }
}
