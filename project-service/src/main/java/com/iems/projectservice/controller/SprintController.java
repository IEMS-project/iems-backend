package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateSprintDto;
import com.iems.projectservice.dto.request.UpdateSprintDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.dto.response.SprintBurndownDto;
import com.iems.projectservice.entity.Sprint;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.service.IssueService;
import com.iems.projectservice.service.ProjectService;
import com.iems.projectservice.service.SprintBurndownService;
import com.iems.projectservice.service.SprintService;
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

    @PostMapping
    @Operation(summary = "Create sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_CREATE)
    public ResponseEntity<ApiResponseDto<Sprint>> createSprint(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintDto dto) {
        try {
            UUID userId = projectService.getUserIdFromRequest();
            Sprint sprint = sprintService.createSprint(projectId, dto, userId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Sprint created successfully", sprint));
        } catch (Exception e) {
            log.error("Error creating sprint", e);
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PatchMapping("/{sprintId}")
    @Operation(summary = "Update sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> updateSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintDto dto) {
        try {
            Sprint sprint = sprintService.updateSprint(sprintId, dto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint updated successfully", sprint));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{sprintId}")
    @Operation(summary = "Delete sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            sprintService.deleteSprint(sprintId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping
    @Operation(summary = "Get project sprints")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<List<Sprint>>> getSprints(@PathVariable UUID projectId) {
        try {
            List<Sprint> sprints = sprintService.getSprintsByProject(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprints retrieved successfully", sprints));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/{sprintId}")
    @Operation(summary = "Get sprint by ID")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<Sprint>> getSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            Sprint sprint = sprintService.getSprintById(sprintId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint retrieved successfully", sprint));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{sprintId}/start")
    @Operation(summary = "Start sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> startSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            UUID userId = projectService.getUserIdFromRequest();
            Sprint sprint = sprintService.startSprint(sprintId, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint started successfully", sprint));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{sprintId}/complete")
    @Operation(summary = "Complete sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> completeSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            UUID userId = projectService.getUserIdFromRequest();
            Sprint sprint = sprintService.completeSprint(sprintId, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint completed successfully", sprint));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{sprintId}/cancel")
    @Operation(summary = "Cancel sprint")
    @RequireProjectPermission(ProjectPermission.SPRINT_UPDATE)
    public ResponseEntity<ApiResponseDto<Sprint>> cancelSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            UUID userId = projectService.getUserIdFromRequest();
            Sprint sprint = sprintService.cancelSprint(sprintId, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint cancelled successfully", sprint));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/{sprintId}/issues")
    @Operation(summary = "Get sprint issues")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getSprintIssues(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            List<IssueResponseDto> issues = issueService.getIssuesBySprint(sprintId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Sprint issues retrieved successfully", issues));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/{sprintId}/burndown")
    @Operation(summary = "Get sprint burndown data")
    @RequireProjectPermission(ProjectPermission.SPRINT_READ)
    public ResponseEntity<ApiResponseDto<SprintBurndownDto>> getSprintBurndown(
            @PathVariable UUID projectId,
            @PathVariable UUID sprintId) {
        try {
            SprintBurndownDto burndown = sprintBurndownService.getSprintBurndown(sprintId);
            return ResponseEntity
                    .ok(new ApiResponseDto<>("success", "Sprint burndown retrieved successfully", burndown));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
