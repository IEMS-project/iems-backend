package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectProgressDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.response.ProjectResponseDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.enums.ProjectRole;
import com.iems.projectservice.entity.enums.ProjectStatus;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final UserService userService;
    
    @Transactional
    public ProjectResponseDto createProject(CreateProjectDto createProjectDto, UUID currentUserId) {
        log.info("Creating project: {}", createProjectDto.getName());
        
        // Validate project name uniqueness
        if (projectRepository.existsByName(createProjectDto.getName())) {
            throw new AppException( ProjectErrorCode.PROJECT_NAME_EXISTS);
        }
        
        // Create project
        Project project = new Project();
        project.setName(createProjectDto.getName());
        project.setDescription(createProjectDto.getDescription());
        project.setStartDate(createProjectDto.getStartDate());
        project.setEndDate(createProjectDto.getEndDate());
        project.setManagerId(createProjectDto.getManagerId());
        project.setCreatedBy(currentUserId);
        project.setStatus(ProjectStatus.PLANNING);
        
        Project savedProject = projectRepository.save(project);
        
        // Add manager as project member
        projectMemberService.addMemberToProject(savedProject.getId(), 
                createProjectDto.getManagerId(),
                ProjectRole.PROJECT_MANAGER,
                currentUserId);
        
        return mapToProjectResponseDto(savedProject);
    }
    
    @Transactional
    public ProjectResponseDto updateProject(UUID projectId, UpdateProjectDto updateProjectDto, UUID currentUserId) {
        log.info("Updating project: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new  AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if user has permission to update
        if (!hasPermissionToUpdateProject(project, currentUserId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
        
        // Update project fields
        if (updateProjectDto.getName() != null) {
            if (!updateProjectDto.getName().equals(project.getName()) && 
                projectRepository.existsByName(updateProjectDto.getName())) {
                throw new AppException( ProjectErrorCode.PROJECT_NAME_EXISTS);
            }
            project.setName(updateProjectDto.getName());
        }
        
        if (updateProjectDto.getDescription() != null) {
            project.setDescription(updateProjectDto.getDescription());
        }
        
        if (updateProjectDto.getStartDate() != null) {
            project.setStartDate(updateProjectDto.getStartDate());
        }
        
        if (updateProjectDto.getEndDate() != null) {
            project.setEndDate(updateProjectDto.getEndDate());
        }
        
        if (updateProjectDto.getStatus() != null) {
            project.setStatus(updateProjectDto.getStatus());
        }
        
        if (updateProjectDto.getManagerId() != null && 
            !updateProjectDto.getManagerId().equals(project.getManagerId())) {
            // Update project manager
            project.setManagerId(updateProjectDto.getManagerId());
            projectMemberService.updateMemberRole(projectId, updateProjectDto.getManagerId(), 
                    ProjectRole.PROJECT_MANAGER, currentUserId);
        }
        
        Project updatedProject = projectRepository.save(project);
        
        return mapToProjectResponseDto(updatedProject);
    }
    
    public ProjectResponseDto getProjectById(UUID projectId, UUID currentUserId) {
        log.info("Getting project: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if user has access to project
        if (!hasAccessToProject(project, currentUserId)) {
            throw new  AppException( ProjectErrorCode.PERMISSION_DENIED);
        }
        
        return mapToProjectResponseDto(project);
    }
    
    public List<ProjectResponseDto> getProjectsByMember(UUID userId) {
        log.info("Getting projects for member: {}", userId);
        
        List<Project> projects = projectRepository.findByMemberId(userId);
        return projects.stream().map(this::mapToProjectResponseDto).toList();
    }
    
    public List<ProjectResponseDto> findAllProjects(UUID currentUserId) {
        log.info("Getting all projects for user: {}", currentUserId);
        
        List<Project> projects = projectRepository.findAll();
        return projects.stream().map(this::mapToProjectResponseDto).toList();
    }
    
    public ProjectProgressDto getProjectProgress(UUID projectId, UUID currentUserId) {
        log.info("Getting project progress: {}", projectId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new  AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        if (!hasAccessToProject(project, currentUserId)) {
            throw new  AppException( ProjectErrorCode.PERMISSION_DENIED);
        }
        
        // This would typically integrate with task service to get actual task statistics
        // For now, returning mock data
        return new ProjectProgressDto(10, 5, 3, 2, 1, 50.0);
    }
    
    @Transactional
    public void assignProjectManager(UUID projectId, UUID newManagerId, UUID currentUserId) {
        log.info("Assigning project manager: projectId={}, newManagerId={}", projectId, newManagerId);
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if current user is admin or current project manager
        if (!isAdmin(currentUserId) && !project.getManagerId().equals(currentUserId)) {
            throw new AppException( ProjectErrorCode.PERMISSION_DENIED);
        }
        
        project.setManagerId(newManagerId);
        projectRepository.save(project);
        
        // Update or add new manager as project member
        projectMemberService.updateMemberRole(projectId, newManagerId, 
                ProjectRole.PROJECT_MANAGER, currentUserId);
    }
    
    private boolean hasPermissionToUpdateProject(Project project, UUID userId) {
        return project.getManagerId().equals(userId) || isAdmin(userId);
    }
    
    private boolean hasAccessToProject(Project project, UUID userId) {
        return project.getManagerId().equals(userId) || 
               projectMemberService.isProjectMember(project.getId(), userId) ||
               isAdmin(userId);
    }
    
    private boolean isAdmin(UUID userId) {
        // This would typically check user roles from IAM service
        return userService.isAdmin(userId);
    }
    
    private ProjectResponseDto mapToProjectResponseDto(Project project) {
        ProjectResponseDto dto = new ProjectResponseDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setStatus(project.getStatus());
        dto.setManagerId(project.getManagerId());
        dto.setCreatedBy(project.getCreatedBy());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        
        // Get project members
        List<ProjectMemberResponseDto> members = projectMemberService.getProjectMembers(project.getId());
        dto.setMembers(members);
        
        // Get project progress
        ProjectProgressDto progress = getProjectProgress(project.getId(), project.getManagerId());
        dto.setProgress(progress);
        
        return dto;
    }
}
