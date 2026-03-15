package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectInfoResponse;
import com.iems.projectservice.dto.response.ProjectTableDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.service.ProjectService;
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
@RequestMapping("/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponseDto<Project>> createProject(@Valid @RequestBody CreateProjectDto dto) {
        try {
            Project project = projectService.createProject(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Project created successfully", project));
        } catch (Exception e) {
            log.error("Error creating project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update project")
    public ResponseEntity<ApiResponseDto<Project>> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectDto dto) {
        try {
            Project project = projectService.updateProject(projectId, dto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project updated successfully", project));
        } catch (Exception e) {
            log.error("Error updating project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project")
    public ResponseEntity<ApiResponseDto<Void>> deleteProject(@PathVariable UUID projectId) {
        try {
            projectService.deleteProject(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ApiResponseDto<Project>> getProject(@PathVariable UUID projectId) {
        try {
            Project project = projectService.getProjectById(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project retrieved successfully", project));
        } catch (Exception e) {
            log.error("Error getting project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/all")
    @Operation(summary = "Get all projects")
    public ResponseEntity<ApiResponseDto<List<Project>>> getAllProjects() {
        try {
            List<Project> projects = projectService.getAllProjects();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "All projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting all projects", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/table")
    @Operation(summary = "Get projects table with manager info")
    public ResponseEntity<ApiResponseDto<List<ProjectTableDto>>> getProjectsTable() {
        try {
            List<ProjectTableDto> projects = projectService.getProjectsTable();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting projects table", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/my-projects")
    @Operation(summary = "Get my projects")
    public ResponseEntity<ApiResponseDto<List<Project>>> getMyProjects() {
        try {
            List<Project> projects = projectService.getMyProjects();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "User projects retrieved successfully", projects));
        } catch (Exception e) {
            log.error("Error getting user projects", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/by-ids")
    @Operation(summary = "Get projects by IDs")
    public ResponseEntity<ApiResponseDto<List<ProjectInfoResponse>>> getProjectsByID(
            @RequestBody ProjectIdsDto request) {
        try {
            List<ProjectInfoResponse> data = projectService.getProjectsByID(request);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Projects retrieved successfully", data));
        } catch (Exception e) {
            log.error("Error getting projects", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
