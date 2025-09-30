package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectProgressDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectResponseDto;
import com.iems.projectservice.dto.response.ProjectTableDto;
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
@RequestMapping("/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project")
public class ProjectController {
    
    private final ProjectService projectService;
    
    @PostMapping
    @Operation(summary = "Create a new project", description = "Create a new project with the provided details")
    public ResponseEntity<ApiResponseDto<ProjectResponseDto>> createProject(
            @Valid @RequestBody CreateProjectDto createProjectDto) {
        try {
            ProjectResponseDto project = projectService.createProject(createProjectDto);
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
            @Valid @RequestBody UpdateProjectDto updateProjectDto){
        try {
            ProjectResponseDto project = projectService.updateProject(projectId, updateProjectDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project updated successfully", project));
        } catch (Exception e) {
            log.error("Error updating project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/table")
    @Operation(summary = "Get projects for table", description = "Get basic project info for table display")
    public ResponseEntity<ApiResponseDto<List<ProjectTableDto>>> getProjectsForTable() {
        try {
            List<ProjectTableDto> projects = projectService.getProjectsForTable();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting projects for table", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }


    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID", description = "Retrieve project details by project ID")
    public ResponseEntity<ApiResponseDto<ProjectResponseDto>> getProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId
) {
        try {
            ProjectResponseDto project = projectService.getProjectById(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project retrieved successfully", project));
        } catch (Exception e) {
            log.error("Error getting project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/my-projects")
    @Operation(summary = "Get user's projects", description = "Get all projects where the current user is a member")
    public ResponseEntity<ApiResponseDto<List<ProjectResponseDto>>> getMyProjects() {
        try {
            List<ProjectResponseDto> projects = projectService.getMyProjects();
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
    ) {
        try {
            List<ProjectResponseDto> projects = projectService.findAllProjects();
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
            @PathVariable UUID projectId){
        try {
            ProjectProgressDto progress = projectService.getProjectProgress(projectId);
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
            @RequestParam UUID newManagerId){
        try {
            projectService.assignProjectManager(projectId, newManagerId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project manager assigned successfully", null));
        } catch (Exception e) {
            log.error("Error assigning project manager", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
