package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectProgressDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectResponseDto;
import com.iems.projectservice.dto.external.ProjectInfoDto;
import com.iems.projectservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project")
@CrossOrigin(origins = "*")
public class ProjectController {
    
    private final ProjectService projectService;
    
    @PostMapping
    @Operation(summary = "Create a new project", description = "Create a new project with the provided details")
    public ResponseEntity<ApiResponseDto<ProjectResponseDto>> createProject(
            @Valid @RequestBody CreateProjectDto createProjectDto,
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            ProjectResponseDto project = projectService.createProject(createProjectDto, currentUserId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Project created successfully", project));
        } catch (Exception e) {
            log.error("Error creating project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @PatchMapping("/{projectId}")
    @Operation(summary = "Update an existing project", description = "Update project details by project ID")
    public ResponseEntity<ApiResponseDto<ProjectResponseDto>> updateProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectDto updateProjectDto,
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            ProjectResponseDto project = projectService.updateProject(projectId, updateProjectDto, currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project updated successfully", project));
        } catch (Exception e) {
            log.error("Error updating project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID", description = "Retrieve project details by project ID")
    public ResponseEntity<ApiResponseDto<ProjectResponseDto>> getProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            ProjectResponseDto project = projectService.getProjectById(projectId, currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project retrieved successfully", project));
        } catch (Exception e) {
            log.error("Error getting project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/my-projects")
    @Operation(summary = "Get user's projects", description = "Get all projects where the current user is a member")
    public ResponseEntity<ApiResponseDto<List<ProjectResponseDto>>> getMyProjects(
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            List<ProjectResponseDto> projects = projectService.getProjectsByMember(currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "User projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting user projects", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/all")
    @Operation(summary = "Get all projects", description = "Get all projects in the system")
    public ResponseEntity<ApiResponseDto<List<ProjectResponseDto>>> getAllProjects(
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            List<ProjectResponseDto> projects = projectService.findAllProjects(currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "All projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting all projects", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/{projectId}/progress")
    @Operation(summary = "Get project progress", description = "Get project progress and task statistics")
    public ResponseEntity<ApiResponseDto<ProjectProgressDto>> getProjectProgress(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            ProjectProgressDto progress = projectService.getProjectProgress(projectId, currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project progress retrieved successfully", progress));
        } catch (Exception e) {
            log.error("Error getting project progress", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @PutMapping("/{projectId}/assign-manager")
    @Operation(summary = "Assign project manager", description = "Assign a new project manager to the project (Admin only)")
    public ResponseEntity<ApiResponseDto<Void>> assignProjectManager(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "New manager user ID", required = true)
            @RequestParam UUID newManagerId,
            @Parameter(description = "Current user ID", required = true)
            @RequestHeader("X-User-ID") UUID currentUserId) {
        try {
            projectService.assignProjectManager(projectId, newManagerId, currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project manager assigned successfully", null));
        } catch (Exception e) {
            log.error("Error assigning project manager", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    // External API for other services
    @GetMapping("/external/{projectId}")
    @Operation(summary = "Get project info for external services", description = "Get basic project info for service-to-service communication")
    public ResponseEntity<ProjectInfoDto> getProjectInfoForExternal(@PathVariable UUID projectId) {
        try {
            ProjectResponseDto project = projectService.getProjectById(projectId, projectId); // Using projectId as currentUserId for external calls
            if (project != null) {
                ProjectInfoDto projectInfo = new ProjectInfoDto();
                projectInfo.setId(project.getId());
                projectInfo.setName(project.getName());
                projectInfo.setDescription(project.getDescription());
                projectInfo.setStatus(project.getStatus().toString());
                projectInfo.setManagerId(project.getManagerId());
                projectInfo.setStartDate(project.getStartDate());
                projectInfo.setEndDate(project.getEndDate());
                return ResponseEntity.ok(projectInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting project info for external service", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
