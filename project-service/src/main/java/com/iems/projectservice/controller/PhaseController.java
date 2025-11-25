package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.CreatePhaseDto;
import com.iems.projectservice.dto.request.UpdatePhaseDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.PhaseResponseDto;
import com.iems.projectservice.service.PhaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/phases")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Phase")
public class PhaseController {
    
    private final PhaseService phaseService;
    
    @PostMapping
    @Operation(summary = "Create a new phase", description = "Create a new phase for a project")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    public ResponseEntity<ApiResponseDto<PhaseResponseDto>> createPhase(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Valid @RequestBody CreatePhaseDto createPhaseDto) {
        try {
            // Set projectId from path variable
            createPhaseDto.setProjectId(projectId);
            PhaseResponseDto phase = phaseService.createPhase(createPhaseDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Phase created successfully", phase));
        } catch (Exception e) {
            log.error("Error creating phase for project: {}", projectId, e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping
    @Operation(summary = "Get all phases of a project", description = "Get all phases ordered by sort order")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ResponseEntity<ApiResponseDto<List<PhaseResponseDto>>> getPhasesByProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId) {
        try {
            List<PhaseResponseDto> phases = phaseService.getPhasesByProject(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Phases retrieved successfully", phases));
        } catch (Exception e) {
            log.error("Error getting phases for project: {}", projectId, e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/{phaseId}")
    @Operation(summary = "Get a phase by ID", description = "Get details of a specific phase")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ResponseEntity<ApiResponseDto<PhaseResponseDto>> getPhaseById(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Phase ID", required = true)
            @PathVariable UUID phaseId) {
        try {
            PhaseResponseDto phase = phaseService.getPhaseById(projectId, phaseId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Phase retrieved successfully", phase));
        } catch (Exception e) {
            log.error("Error getting phase: {} for project: {}", phaseId, projectId, e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @PatchMapping("/{phaseId}")
    @Operation(summary = "Update a phase", description = "Update details of a specific phase")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    public ResponseEntity<ApiResponseDto<PhaseResponseDto>> updatePhase(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Phase ID", required = true)
            @PathVariable UUID phaseId,
            @Valid @RequestBody UpdatePhaseDto updatePhaseDto) {
        try {
            PhaseResponseDto phase = phaseService.updatePhase(projectId, phaseId, updatePhaseDto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Phase updated successfully", phase));
        } catch (Exception e) {
            log.error("Error updating phase: {} for project: {}", phaseId, projectId, e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @DeleteMapping("/{phaseId}")
    @Operation(summary = "Delete a phase", description = "Delete a specific phase")
    @PreAuthorize("hasAuthority('PROJECT_UPDATE')")
    public ResponseEntity<ApiResponseDto<Void>> deletePhase(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Phase ID", required = true)
            @PathVariable UUID phaseId) {
        try {
            phaseService.deletePhase(projectId, phaseId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Phase deleted successfully", null));
        } catch (Exception e) {
            log.error("Error deleting phase: {} for project: {}", phaseId, projectId, e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
