package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreatePhaseDto;
import com.iems.projectservice.dto.request.UpdatePhaseDto;
import com.iems.projectservice.dto.response.PhaseResponseDto;
import com.iems.projectservice.entity.Phase;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.PhaseRepository;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhaseService {
    
    private final PhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;
    
    /**
     * Create a new phase
     */
    @Transactional
    public PhaseResponseDto createPhase(CreatePhaseDto createPhaseDto) {
        log.info("Creating phase for project: {}", createPhaseDto.getProjectId());
        
        // Verify project exists
        Project project = projectRepository.findById(createPhaseDto.getProjectId())
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // If sortOrder is not provided, set it to max + 1
        Integer sortOrder = createPhaseDto.getSortOrder();
        if (sortOrder == null) {
            Integer maxSortOrder = phaseRepository.findMaxSortOrderByProjectId(createPhaseDto.getProjectId());
            sortOrder = (maxSortOrder == null ? 0 : maxSortOrder) + 1;
        }
        
        Phase phase = new Phase();
        phase.setProject(project);
        phase.setName(createPhaseDto.getName());
        phase.setDescription(createPhaseDto.getDescription());
        phase.setGoal(createPhaseDto.getGoal());
        phase.setStartDate(createPhaseDto.getStartDate());
        phase.setEndDate(createPhaseDto.getEndDate());
        phase.setSortOrder(sortOrder);
        
        Phase savedPhase = phaseRepository.save(phase);
        log.info("Phase created successfully with ID: {}", savedPhase.getId());
        
        return mapToResponseDto(savedPhase);
    }
    
    /**
     * Get all phases of a project
     */
    public List<PhaseResponseDto> getPhasesByProject(UUID projectId) {
        log.info("Getting phases for project: {}", projectId);
        
        // Verify project exists
        projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        List<Phase> phases = phaseRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        return phases.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a single phase by ID
     */
    public PhaseResponseDto getPhaseById(UUID projectId, UUID phaseId) {
        log.info("Getting phase: {} for project: {}", phaseId, projectId);
        
        Phase phase = phaseRepository.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PHASE_NOT_FOUND));
        
        return mapToResponseDto(phase);
    }
    
    /**
     * Update a phase
     */
    @Transactional
    public PhaseResponseDto updatePhase(UUID projectId, UUID phaseId, UpdatePhaseDto updatePhaseDto) {
        log.info("Updating phase: {} for project: {}", phaseId, projectId);
        
        Phase phase = phaseRepository.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PHASE_NOT_FOUND));
        
        // Update fields if provided
        if (updatePhaseDto.getName() != null) {
            phase.setName(updatePhaseDto.getName());
        }
        if (updatePhaseDto.getDescription() != null) {
            phase.setDescription(updatePhaseDto.getDescription());
        }
        if (updatePhaseDto.getGoal() != null) {
            phase.setGoal(updatePhaseDto.getGoal());
        }
        if (updatePhaseDto.getStartDate() != null) {
            phase.setStartDate(updatePhaseDto.getStartDate());
        }
        if (updatePhaseDto.getEndDate() != null) {
            phase.setEndDate(updatePhaseDto.getEndDate());
        }
        if (updatePhaseDto.getSortOrder() != null) {
            phase.setSortOrder(updatePhaseDto.getSortOrder());
        }
        
        Phase updatedPhase = phaseRepository.save(phase);
        log.info("Phase updated successfully: {}", phaseId);
        
        return mapToResponseDto(updatedPhase);
    }
    
    /**
     * Delete a phase
     */
    @Transactional
    public void deletePhase(UUID projectId, UUID phaseId) {
        log.info("Deleting phase: {} for project: {}", phaseId, projectId);
        
        Phase phase = phaseRepository.findByIdAndProjectId(phaseId, projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PHASE_NOT_FOUND));
        
        phaseRepository.delete(phase);
        log.info("Phase deleted successfully: {}", phaseId);
    }
    
    /**
     * Map Phase entity to PhaseResponseDto
     */
    private PhaseResponseDto mapToResponseDto(Phase phase) {
        return PhaseResponseDto.builder()
                .id(phase.getId())
                .projectId(phase.getProject().getId())
                .name(phase.getName())
                .description(phase.getDescription())
                .goal(phase.getGoal())
                .startDate(phase.getStartDate())
                .endDate(phase.getEndDate())
                .sortOrder(phase.getSortOrder())
                .createdAt(phase.getCreatedAt())
                .updatedAt(phase.getUpdatedAt())
                .build();
    }
}
