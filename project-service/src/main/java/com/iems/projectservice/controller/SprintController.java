package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateSprintDto;
import com.iems.projectservice.dto.request.UpdateSprintDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.dto.response.SprintBurndownDto;
import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.service.IssueService;
import com.iems.projectservice.service.ProjectService;
import com.iems.projectservice.service.SprintBurndownService;
import com.iems.projectservice.service.SprintService;
import com.iems.projectservice.service.SubscriptionLimitService;
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
@RequestMapping("/projects/{projectId}/sprints")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sprint")
public class SprintController {

    private final SprintService sprintService;
    private final SprintBurndownService sprintBurndownService;
    private final IssueService issueService;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final SubscriptionLimitService subscriptionLimitService;

    @PostMapping
    @Operation(summary = "Create sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_CREATE)
    public ResponseEntity<ApiResponseDto<Sprint>> createSprint(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintDto dto) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        Sprint sprint = sprintService.createSprint(projectId, dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Sprint created successfully", sprint));
    }

    @PatchMapping("/{sprintId}")
    @Operation(summary = "Update sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> updateSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintDto dto) throws AppException {
        Sprint sprint = sprintService.updateSprint(sprintId, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint updated successfully", sprint));
    }

    @DeleteMapping("/{sprintId}")
    @Operation(summary = "Delete sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        sprintService.deleteSprint(sprintId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint deleted successfully", null));
    }

    @GetMapping
    @Operation(summary = "Get project sprints")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<List<Sprint>>> getSprints(@PathVariable UUID projectId) throws AppException {
        List<Sprint> sprints = sprintService.getSprintsByProject(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprints retrieved successfully", sprints));
    }

    @GetMapping("/{sprintId}")
    @Operation(summary = "Get sprint by ID")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<Sprint>> getSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        Sprint sprint = sprintService.getSprintById(sprintId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint retrieved successfully", sprint));
    }

    @PostMapping("/{sprintId}/start")
    @Operation(summary = "Start sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> startSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        Sprint sprint = sprintService.startSprint(sprintId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint started successfully", sprint));
    }

    @PostMapping("/{sprintId}/complete")
    @Operation(summary = "Complete sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> completeSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        Sprint sprint = sprintService.completeSprint(sprintId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint completed successfully", sprint));
    }

    @PostMapping("/{sprintId}/cancel")
    @Operation(summary = "Cancel sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> cancelSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        Sprint sprint = sprintService.cancelSprint(sprintId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint cancelled successfully", sprint));
    }

    @GetMapping("/{sprintId}/issues")
    @Operation(summary = "Get sprint issues")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getSprintIssues(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        List<IssueResponseDto> issues = issueService.getIssuesBySprint(sprintId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint issues retrieved successfully", issues));
    }

    @GetMapping("/{sprintId}/burndown")
    @Operation(summary = "Get sprint burndown data")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<SprintBurndownDto>> getSprintBurndown(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) throws AppException {
        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        subscriptionLimitService.checkBurndownAccess(ownerSub);

        SprintBurndownDto burndown = sprintBurndownService.getSprintBurndown(sprintId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint burndown retrieved successfully", burndown));
    }
}
