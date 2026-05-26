package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.MyProjectResponseDto;
import com.iems.projectservice.dto.response.PagedResponseDto;
import com.iems.projectservice.dto.response.ProjectTableDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
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
    public ResponseEntity<ApiResponseDto<Project>> createProject(@Valid @RequestBody CreateProjectDto dto) throws AppException {
        Project project = projectService.createProject(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Project created successfully", project));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update project")
    @RequireProjectPermission(ProjectPermission.PROJECT_UPDATE)
    public ResponseEntity<ApiResponseDto<Project>> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectDto dto) throws AppException {
        Project project = projectService.updateProject(projectId, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Project updated successfully", project));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project")
    @RequireProjectPermission(ProjectPermission.PROJECT_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteProject(@PathVariable UUID projectId) throws AppException {
        projectService.deleteProject(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Project deleted successfully", null));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    @RequireProjectPermission(ProjectPermission.PROJECT_READ)
    public ResponseEntity<ApiResponseDto<Project>> getProject(@PathVariable UUID projectId) throws AppException {
        Project project = projectService.getProjectById(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Project retrieved successfully", project));
    }

    @GetMapping("/table")
    @Operation(summary = "Get projects table with manager info")
    public ResponseEntity<ApiResponseDto<List<ProjectTableDto>>> getProjectsTable() throws AppException {
        List<ProjectTableDto> projects = projectService.getProjectsTable();
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Projects retrieved successfully", projects));
    }

    @GetMapping("/my-projects")
    @Operation(summary = "Get my projects")
    public ResponseEntity<ApiResponseDto<PagedResponseDto<MyProjectResponseDto>>> getMyProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) throws AppException {
        PagedResponseDto<MyProjectResponseDto> projects = projectService.getMyProjects(page, size);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "User projects retrieved successfully", projects));
    }

}
