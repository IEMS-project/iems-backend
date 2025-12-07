package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.CreateProjectRepositoryDto;
import com.iems.projectservice.dto.request.UpdateProjectRepositoryDto;
import com.iems.projectservice.dto.response.ProjectRepositoryDto;
import com.iems.projectservice.service.ProjectRepositoryService;
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
@RequestMapping("/api/project-repositories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project Repository", description = "Project Repository Management APIs")
public class ProjectRepositoryController {

    private final ProjectRepositoryService projectRepositoryService;

    @PostMapping
    @Operation(summary = "Create a new repository for a project")
    public ResponseEntity<ProjectRepositoryDto> createRepository(
            @Valid @RequestBody CreateProjectRepositoryDto dto) {
        log.info("REST request to create repository for project: {}", dto.getProjectId());
        ProjectRepositoryDto result = projectRepositoryService.createRepository(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all repositories for a project")
    public ResponseEntity<List<ProjectRepositoryDto>> getRepositoriesByProjectId(
            @PathVariable UUID projectId) {
        log.info("REST request to get repositories for project: {}", projectId);
        List<ProjectRepositoryDto> result = projectRepositoryService.getRepositoriesByProjectId(projectId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get repository by id")
    public ResponseEntity<ProjectRepositoryDto> getRepositoryById(@PathVariable UUID id) {
        log.info("REST request to get repository: {}", id);
        ProjectRepositoryDto result = projectRepositoryService.getRepositoryById(id);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a repository")
    public ResponseEntity<ProjectRepositoryDto> updateRepository(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRepositoryDto dto) {
        log.info("REST request to update repository: {}", id);
        ProjectRepositoryDto result = projectRepositoryService.updateRepository(id, dto);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a repository")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID id) {
        log.info("REST request to delete repository: {}", id);
        projectRepositoryService.deleteRepository(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/project/{projectId}")
    @Operation(summary = "Delete all repositories for a project")
    public ResponseEntity<Void> deleteRepositoriesByProjectId(@PathVariable UUID projectId) {
        log.info("REST request to delete all repositories for project: {}", projectId);
        projectRepositoryService.deleteRepositoriesByProjectId(projectId);
        return ResponseEntity.noContent().build();
    }
}
