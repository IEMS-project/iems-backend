package com.iems.projectservice.service;

import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectProgressDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.response.ProjectResponseDto;
import com.iems.projectservice.dto.response.ProjectTableDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.enums.ProjectRole;
import com.iems.projectservice.entity.enums.ProjectStatus;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }

    private Optional<UserDetailDto> getUserById(UUID userId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserById(userId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }
    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        UserDetailDto dto = new UserDetailDto();
        dto.setId(UUID.fromString(userData.get("id").toString()));
        dto.setFirstName((String) userData.get("firstName"));
        dto.setLastName((String) userData.get("lastName"));
        dto.setEmail((String) userData.get("email"));
        dto.setAddress((String) userData.get("address"));
        dto.setPhone((String) userData.get("phone"));

        // Handle Date objects - convert to string
        Object dob = userData.get("dob");
        dto.setDob(dob != null ? dob.toString() : null);

        // Handle enum objects - convert to string
        Object gender = userData.get("gender");
        dto.setGender(gender != null ? gender.toString() : null);

        dto.setPersonalID((String) userData.get("personalID"));
        dto.setImage((String) userData.get("image"));
        dto.setBankAccountNumber((String) userData.get("bankAccountNumber"));
        dto.setBankName((String) userData.get("bankName"));

        // Handle enum objects - convert to string
        Object contractType = userData.get("contractType");
        dto.setContractType(contractType != null ? contractType.toString() : null);

        // Handle Date objects - convert to string
        Object startDate = userData.get("startDate");
        dto.setStartDate(startDate != null ? startDate.toString() : null);

        dto.setRole((String) userData.get("role"));
        return dto;
    }


    @Transactional
    public ProjectResponseDto createProject(CreateProjectDto createProjectDto) {
        log.info("Creating project: {}", createProjectDto.getName());
        
        // Validate project name uniqueness
        if (projectRepository.existsByName(createProjectDto.getName())) {
            throw new AppException( ProjectErrorCode.PROJECT_NAME_EXISTS);
        }
        UUID currentUserId = getUserIdFromRequest();
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
                ProjectRole.PROJECT_MANAGER);
        
        return mapToProjectResponseDto(savedProject);
    }
    
    @Transactional
    public ProjectResponseDto updateProject(UUID projectId, UpdateProjectDto updateProjectDto) {
        log.info("Updating project: {}", projectId);
        UUID currentUserId = getUserIdFromRequest();
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
                    ProjectRole.PROJECT_MANAGER);
        }
        
        Project updatedProject = projectRepository.save(project);
        
        return mapToProjectResponseDto(updatedProject);
    }

    public List<ProjectResponseDto> getMyProjects(){
        UUID userId = getUserIdFromRequest();
        List<Project> projects = projectRepository.findByMemberId(userId);
        return projects.stream().map(this::mapToProjectResponseDto).toList();
    }

    public ProjectResponseDto getProjectById(UUID projectId) {
        log.info("Getting project: {}", projectId);
        UUID currentUserId = getUserIdFromRequest();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if user has access to project
        if (!hasAccessToProject(project, currentUserId)) {
            throw new  AppException( ProjectErrorCode.PERMISSION_DENIED);
        }
        
        return mapToProjectResponseDto(project);
    }

    public List<ProjectTableDto> getProjectsForTable() {
        List<Project> projects = projectRepository.findAll();

        return projects.stream().map(project -> {
            ProjectTableDto dto = new ProjectTableDto();
            dto.setId(project.getId());
            dto.setName(project.getName());
            dto.setStatus(project.getStatus());
            dto.setDescription(project.getDescription());
            dto.setManagerId(project.getManagerId());
            dto.setStartDate(project.getStartDate());
            dto.setEndDate(project.getEndDate());

            Optional<UserDetailDto> managerOpt = getUserById(project.getManagerId());
            if (managerOpt.isPresent()) {
                UserDetailDto manager = managerOpt.get();
                dto.setManagerName(manager.getFirstName() + " " + manager.getLastName());
                dto.setManagerEmail(manager.getEmail());
                dto.setManagerImage(manager.getImage());
            }
            return dto;
        }).toList();
    }

    public List<ProjectResponseDto> getProjectsByMember(UUID userId) {
        log.info("Getting projects for member: {}", userId);
        
        List<Project> projects = projectRepository.findByMemberId(userId);
        return projects.stream().map(this::mapToProjectResponseDto).toList();
    }
    
    public List<ProjectResponseDto> findAllProjects() {
        UUID currentUserId = getUserIdFromRequest();
        log.info("Getting all projects for user: {}", currentUserId);

        List<Project> projects = projectRepository.findAll();
        return projects.stream().map(this::mapToProjectResponseDto).toList();
    }
    
    public ProjectProgressDto getProjectProgress(UUID projectId) {
        log.info("Getting project progress: {}", projectId);
        UUID currentUserId = getUserIdFromRequest();
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
    public void assignProjectManager(UUID projectId, UUID newManagerId) {
        log.info("Assigning project manager: projectId={}, newManagerId={}", projectId, newManagerId);
        UUID currentUserId = getUserIdFromRequest();
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
                ProjectRole.PROJECT_MANAGER);
    }
    
    private boolean hasPermissionToUpdateProject(Project project, UUID userId) {
        return project.getManagerId().equals(userId) || isAdmin(userId);
    }
    
    private boolean hasAccessToProject(Project project, UUID userId) {
        return project.getManagerId().equals(userId) ||
               projectMemberService.isProjectMember(project.getId(), userId) ||
               isAdmin(userId);
    }

    public boolean isAdmin(UUID userId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails)) {
                return false;
            }
            
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            
            // Check if user has ADMIN role
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
            log.info("Checking admin status for userId: {} - isAdmin: {}", userId, isAdmin);
            return isAdmin;
        } catch (Exception e) {
            log.error("Error checking admin status for userId: {}", userId, e);
            return false;
        }
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
        Optional<UserDetailDto> userOpt = this.getUserById(dto.getManagerId());
        if (userOpt.isPresent()) {
            UserDetailDto userDetailDto = userOpt.get();
            dto.setManagerName(userDetailDto.getFirstName() + " " + userDetailDto.getLastName());
            dto.setManagerEmail(userDetailDto.getEmail());
            dto.setManagerImage(userDetailDto.getImage());
        } else {
            dto.setManagerName("-");
            dto.setManagerEmail("-");
            dto.setManagerImage(null);
        }
        dto.setCreatedBy(project.getCreatedBy());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        
        // Get project members
        List<ProjectMemberResponseDto> members = projectMemberService.getProjectMembers(project.getId());
        dto.setMembers(members);
        
        // Get project progress
        ProjectProgressDto progress = getProjectProgress(project.getId());
        dto.setProgress(progress);
        
        return dto;
    }
}
