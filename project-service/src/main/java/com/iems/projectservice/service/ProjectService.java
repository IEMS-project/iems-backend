package com.iems.projectservice.service;

import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.response.PhaseProgressDto;
import com.iems.projectservice.dto.response.ProjectProgressDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.*;
import com.iems.projectservice.entity.Phase;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.entity.enums.ProjectStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.PhaseRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.security.JwtUserDetails;
import com.iems.projectservice.service.ITaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final ProjectAllowedRoleService projectAllowedRoleService;
    private final PhaseRepository phaseRepository;
    private final ITaskService taskService;

    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();
        return userId;
    }

    private Optional<UserDetailDto> getUserById(UUID accountId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserByAccountId(accountId);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }

            return Optional.empty();
        } catch (Exception e) {
            // Log error and return empty
            System.err.println("Error fetching user by accountId " + accountId + " from User Service: " + e.getMessage());
            return Optional.empty();
        }
    }
    private UserDetailDto convertToUserDetailDto(Map<String, Object> userData) {
        return new UserDetailDto(
                UUID.fromString(userData.get("id").toString()),
                (String) userData.get("firstName"),
                (String) userData.get("lastName"),
                (String) userData.get("email"),
                (String) userData.get("address"),
                (String) userData.get("phone"),
                userData.get("dob") != null ? userData.get("dob").toString() : null,
                userData.get("gender") != null ? userData.get("gender").toString() : null,
                (String) userData.get("personalID"),
                (String) userData.get("image"),
                (String) userData.get("bankAccountNumber"),
                (String) userData.get("bankName"),
                userData.get("contractType") != null ? userData.get("contractType").toString() : null,
                userData.get("startDate") != null ? userData.get("startDate").toString() : null,
                (String) userData.get("role")
        );
    }


    @Transactional
    public ProjectResponseDto createProject(CreateProjectDto createProjectDto) {
        log.info("Creating project: {}", createProjectDto.getName());
        
        // Validate project name uniqueness
        if (projectRepository.existsByName(createProjectDto.getName())) {
            throw new AppException( ProjectErrorCode.PROJECT_NAME_EXISTS);
        }
        UUID currentUserId = getUserIdFromRequest();
        
        // Validate status: only PLANNING and IN_PROGRESS allowed
        ProjectStatus status = createProjectDto.getStatus() != null ? createProjectDto.getStatus() : ProjectStatus.PLANNING;
        if (status != ProjectStatus.PLANNING && status != ProjectStatus.IN_PROGRESS) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        
        // Create project - creator is automatically the PM
        Project project = new Project();
        project.setName(createProjectDto.getName());
        project.setDescription(createProjectDto.getDescription());
        project.setStartDate(createProjectDto.getStartDate());
        project.setEndDate(createProjectDto.getEndDate());
        project.setManagerAccountId(currentUserId);
        project.setCreatedByAccountId(currentUserId);
        project.setStatus(status);
        
        Project savedProject = projectRepository.save(project);
        
        // Add manager as project member with default role (first available role or null)
        UUID managerRoleId = getDefaultManagerRoleId(savedProject.getId());
        if (managerRoleId != null) {
            projectMemberService.addMemberToProject(savedProject.getId(), 
                    currentUserId,
                    managerRoleId);
        }
        
        // Calculate progress for the new project
        Map<UUID, Double> progressMap = calculateProjectsProgress(List.of(savedProject.getId()));
        double progress = progressMap.getOrDefault(savedProject.getId(), 0.0);
        
        return mapToProjectResponseDto(savedProject, progress);
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
            !updateProjectDto.getManagerId().equals(project.getManagerAccountId())) {
            // Update project manager
            project.setManagerAccountId(updateProjectDto.getManagerId());
            UUID managerRoleId = getDefaultManagerRoleId(projectId);
            if (managerRoleId != null) {
                projectMemberService.updateMemberRole(projectId, updateProjectDto.getManagerId(), 
                        managerRoleId);
            }
        }
        
        Project updatedProject = projectRepository.save(project);
        
        // Calculate progress for the updated project
        Map<UUID, Double> progressMap = calculateProjectsProgress(List.of(updatedProject.getId()));
        double progress = progressMap.getOrDefault(updatedProject.getId(), 0.0);
        
        return mapToProjectResponseDto(updatedProject, progress);
    }

    public List<MyProjectResponseDto> getMyProjects(){
        UUID accountId = getUserIdFromRequest();
        List<Project> projects = projectRepository.findByMemberAccountId(accountId);
        
        // Get project IDs
        List<UUID> projectIds = projects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());
        
        // Get progress data from task service
        Map<UUID, Double> progressMap = calculateProjectsProgress(projectIds);
        
        return projects.stream()
                .map(project -> mapToMyProjectResponseDto(project, progressMap.getOrDefault(project.getId(), 0.0)))
                .collect(Collectors.toList());
    }

    public ProjectDetailResponseDto getProjectById(UUID projectId) {
        log.info("Getting project: {}", projectId);
        UUID currentUserId = getUserIdFromRequest();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // Check permission
//        if (!hasAccessToProject(project, currentUserId)) {
//            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
//        }

        Map<UUID, Double> progressMap = calculateProjectsProgress(List.of(projectId));
        double progress = progressMap.getOrDefault(projectId, 0.0);

        return mapToProjectDetailResponseDto(project, progress);
    }

    public List<ProjectTableDto> getProjectsForTable() {
        List<Project> projects = projectRepository.findAll();

        // Collect all manager IDs
        Set<UUID> managerAccountIds = projects.stream()
                .map(Project::getManagerAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Fetch manager info
        Map<UUID, UserDetailDto> managerMap = fetchManagersByIds(managerAccountIds);

        // Map projects to DTO
        return projects.stream().map(project -> {
            ProjectTableDto dto = new ProjectTableDto();
            dto.setId(project.getId());
            dto.setName(project.getName());
            dto.setStatus(project.getStatus());
            dto.setDescription(project.getDescription());
            dto.setManagerId(project.getManagerAccountId());
            dto.setStartDate(project.getStartDate());
            dto.setEndDate(project.getEndDate());

            if (project.getManagerAccountId() != null && managerMap.containsKey(project.getManagerAccountId())) {
                UserDetailDto manager = managerMap.get(project.getManagerAccountId());
                dto.setManagerName(manager.getFirstName() + " " + manager.getLastName());
                dto.setManagerEmail(manager.getEmail());
                dto.setManagerImage(manager.getImage());
            }

            return dto;
        }).toList();
    }

    private Map<UUID, UserDetailDto> fetchManagersByIds(Set<UUID> managerIds) {
        if (managerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            com.iems.projectservice.dto.request.AccountIdsDto accountIdsDto = new com.iems.projectservice.dto.request.AccountIdsDto();
            accountIdsDto.setAccountIds(managerIds);

            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUsersByAccountIds(accountIdsDto);

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                Object dataObj = response.getBody().get("data");
                if (dataObj instanceof List<?> usersList) {
                    return usersList.stream()
                            .filter(item -> item instanceof Map<?, ?>)
                            .map(item -> (Map<String, Object>) item)
                            .map(this::convertToUserDetailDto)
                            .collect(Collectors.toMap(UserDetailDto::getId, user -> user));
                }
            }
        } catch (Exception e) {
            log.error("Error fetching managers from User Service", e);
        }

        return Collections.emptyMap();
    }


    public List<ProjectResponseDto> getProjectsByMember(UUID accountId) {
        log.info("Getting projects for member: {}", accountId);
        
        List<Project> projects = projectRepository.findByMemberAccountId(accountId);
        
        // Get project IDs
        List<UUID> projectIds = projects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());
        
        // Get progress data from task service
        Map<UUID, Double> progressMap = calculateProjectsProgress(projectIds);
        
        return projects.stream()
                .map(project -> mapToProjectResponseDto(project, progressMap.getOrDefault(project.getId(), 0.0)))
                .collect(Collectors.toList());
    }
    
    public List<ProjectResponseDto> findAllProjects() {
        UUID currentUserId = getUserIdFromRequest();
        log.info("Getting all projects for user: {}", currentUserId);

        List<Project> projects = projectRepository.findAll();
        
        // Get project IDs
        List<UUID> projectIds = projects.stream()
                .map(Project::getId)
                .collect(Collectors.toList());
        
        // Get progress data from task service
        Map<UUID, Double> progressMap = calculateProjectsProgress(projectIds);
        
        return projects.stream()
                .map(project -> mapToProjectResponseDto(project, progressMap.getOrDefault(project.getId(), 0.0)))
                .collect(Collectors.toList());
    }

    public ProjectProgressDto getProjectProgress(UUID projectId) {
        log.info("Getting detailed progress for project: {}", projectId);
        
        // Verify project exists
        projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Get progress from task service
        List<ProjectProgressDto> progressList = taskService.getProjectsProgress(List.of(projectId));
        
        if (progressList.isEmpty()) {
            // Return empty progress if no data available
            ProjectProgressDto emptyProgress = new ProjectProgressDto();
            emptyProgress.setProjectId(projectId);
            emptyProgress.setPhasesProgress(new ArrayList<>());
            return emptyProgress;
        }
        
        return progressList.get(0);
    }

    
    @Transactional
    public void assignProjectManager(UUID projectId, UUID newManagerId) {
        log.info("Assigning project manager: projectId={}, newManagerId={}", projectId, newManagerId);
        UUID currentUserId = getUserIdFromRequest();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException( ProjectErrorCode.PROJECT_NOT_FOUND));
        
        // Check if current user is admin or current project manager
        if (!isAdmin(currentUserId) && !project.getManagerAccountId().equals(currentUserId)) {
            throw new AppException( ProjectErrorCode.PERMISSION_DENIED);
        }
        
        project.setManagerAccountId(newManagerId);
        projectRepository.save(project);
        
        // Update or add new manager as project member
        UUID managerRoleId = getDefaultManagerRoleId(projectId);
        if (managerRoleId != null) {
            projectMemberService.updateMemberRole(projectId, newManagerId, 
                    managerRoleId);
        }
    }
    
    private boolean hasPermissionToUpdateProject(Project project, UUID accountId) {
        return project.getManagerAccountId().equals(accountId) || isAdmin(accountId);
    }
    
    private boolean hasAccessToProject(Project project, UUID accountId) {
        return project.getManagerAccountId().equals(accountId) ||
               projectMemberService.isProjectMember(project.getId(), accountId) ||
               isAdmin(accountId);
    }

    public boolean isAdmin(UUID accountId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails)) {
                return false;
            }
            
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            
            // Check if user has ADMIN role
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
            log.info("Checking admin status for accountId: {} - isAdmin: {}", accountId, isAdmin);
            return isAdmin;
        } catch (Exception e) {
            log.error("Error checking admin status for accountId: {}", accountId, e);
            return false;
        }
    }
    
    /**
     * Calculate project progress based on phases and task completion
     * Formula: Total Progress = Sum(Phase Progress × Phase Weight)
     * Where Phase Weight = 100% / Number of Phases
     */
    private Map<UUID, Double> calculateProjectsProgress(List<UUID> projectIds) {
        Map<UUID, Double> progressMap = new HashMap<>();
        
        if (projectIds.isEmpty()) {
            return progressMap;
        }
        
        try {
            // Get phases for all projects
            List<Phase> allPhases = phaseRepository.findByProjectIdIn(projectIds);
            Map<UUID, List<Phase>> phasesByProject = allPhases.stream()
                    .collect(Collectors.groupingBy(phase -> phase.getProject().getId()));
            
            // Get task progress from task service
            List<ProjectProgressDto> taskProgressList = taskService.getProjectsProgress(projectIds);
            Map<UUID, ProjectProgressDto> taskProgressMap = taskProgressList.stream()
                    .collect(Collectors.toMap(ProjectProgressDto::getProjectId, p -> p));
            
            // Calculate progress for each project
            for (UUID projectId : projectIds) {
                List<Phase> projectPhases = phasesByProject.getOrDefault(projectId, new ArrayList<>());
                
                if (projectPhases.isEmpty()) {
                    // No phases, set progress to 0
                    progressMap.put(projectId, 0.0);
                    continue;
                }
                
                ProjectProgressDto taskProgress = taskProgressMap.get(projectId);
                if (taskProgress == null || taskProgress.getPhasesProgress() == null) {
                    // No task data, set progress to 0
                    progressMap.put(projectId, 0.0);
                    continue;
                }
                
                // Create map of phase progress
                Map<UUID, Double> phaseProgressMap = taskProgress.getPhasesProgress().stream()
                        .collect(Collectors.toMap(PhaseProgressDto::getPhaseID, PhaseProgressDto::getProgress));
                
                // Calculate total progress
                int totalPhases = projectPhases.size();
                double phaseWeight = 100.0 / totalPhases; // Each phase has equal weight
                
                double totalProgress = projectPhases.stream()
                        .mapToDouble(phase -> {
                            double phaseProgress = phaseProgressMap.getOrDefault(phase.getId(), 0.0);
                            return (phaseProgress / 100.0) * phaseWeight; // Convert progress to contribution
                        })
                        .sum();
                
                progressMap.put(projectId, totalProgress);
            }
            
        } catch (Exception e) {
            log.error("Error calculating project progress: {}", e.getMessage());
            // Return empty map or 0 progress for all projects
            projectIds.forEach(id -> progressMap.put(id, 0.0));
        }
        
        return progressMap;
    }

    private ProjectDetailResponseDto mapToProjectDetailResponseDto(Project project, double progress) {
        ProjectDetailResponseDto dto = new ProjectDetailResponseDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setStatus(project.getStatus());
        dto.setManagerId(project.getManagerAccountId());
        dto.setCreatedBy(project.getCreatedByAccountId());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // Fetch manager info
        if (project.getManagerAccountId() != null) {
            Map<UUID, UserDetailDto> managerMap = fetchManagersByIds(Set.of(project.getManagerAccountId()));
            if (managerMap.containsKey(project.getManagerAccountId())) {
                UserDetailDto manager = managerMap.get(project.getManagerAccountId());
                dto.setManagerName(manager.getFirstName() + " " + manager.getLastName());
                dto.setManagerEmail(manager.getEmail());
                dto.setManagerImage(manager.getImage());
            }
        }

        // progress giống MyProject
        dto.setProgress(Math.round(progress * 100.0) / 100.0);

        return dto;
    }


    /**
     * Calculate progress for a single project including phase details
     */
    private ProjectProgressDto calculateSingleProjectProgress(UUID projectId) {
        ProjectProgressDto progressDto = new ProjectProgressDto();
        progressDto.setProjectId(projectId);
        
        try {
            // Get phases for the project
            List<Phase> projectPhases = phaseRepository.findByProjectIdOrderBySortOrderAsc(projectId);
            
            if (projectPhases.isEmpty()) {
                progressDto.setPhasesProgress(new ArrayList<>());
                return progressDto;
            }
            
            // Get task progress from task service
            List<ProjectProgressDto> taskProgressList = taskService.getProjectsProgress(Collections.singletonList(projectId));
            
            if (taskProgressList.isEmpty() || taskProgressList.get(0).getPhasesProgress() == null) {
                // No task data, set all phases to 0 progress
                List<PhaseProgressDto> phasesProgress = projectPhases.stream()
                        .map(phase -> PhaseProgressDto.builder()
                                .phaseID(phase.getId())
                                .progress(0.0)
                                .build())
                        .collect(Collectors.toList());
                progressDto.setPhasesProgress(phasesProgress);
                return progressDto;
            }
            
            // Use the phases progress from task service
            progressDto.setPhasesProgress(taskProgressList.get(0).getPhasesProgress());
            
        } catch (Exception e) {
            log.error("Error calculating project progress for project {}: {}", projectId, e.getMessage());
            progressDto.setPhasesProgress(new ArrayList<>());
        }
        
        return progressDto;
    }

    private MyProjectResponseDto mapToMyProjectResponseDto(Project project, double progress) {
        MyProjectResponseDto dto = new MyProjectResponseDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setStatus(project.getStatus());
        dto.setCreatedBy(project.getCreatedByAccountId());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        dto.setProgress(Math.round(progress * 100.0) / 100.0);
        
        return dto;
    }

    private ProjectResponseDto mapToProjectResponseDto(Project project, double progress) {
        ProjectResponseDto dto = new ProjectResponseDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setStatus(project.getStatus());
        dto.setManagerId(project.getManagerAccountId());
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
        dto.setCreatedBy(project.getCreatedByAccountId());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        
        // Get project members
        List<ProjectMemberResponseDto> members = projectMemberService.getProjectMembers(project.getId());
        dto.setMembers(members);

        dto.setProgress(Math.round(progress * 100.0) / 100.0);
        
        return dto;
    }
    
    private UUID getDefaultManagerRoleId(UUID projectId) {
        try {
            // Get first available role from project allowed roles
            List<ProjectAllowedRole> allowedRoles = projectAllowedRoleService.list(projectId);
            if (!allowedRoles.isEmpty()) {
                // Try to find a role with "manager" in the name, otherwise use first role
                return allowedRoles.stream()
                    .filter(role -> role.getRoleName().toLowerCase().contains("manager") || 
                                   role.getRoleName().toLowerCase().contains("quản lý"))
                    .map(ProjectAllowedRole::getId)
                    .findFirst()
                    .orElse(allowedRoles.get(0).getId());
            }
            return null;
        } catch (Exception e) {
            log.warn("Could not find default manager role for project {}: {}", projectId, e.getMessage());
            return null;
        }
    }

    public List<ProjectInfoResponse> getProjectsByID(ProjectIdsDto projectIds) {
        List<ProjectInfoResponse> list = new ArrayList<>();
        for (UUID projectId : projectIds.getIds()) {
            Project project = projectRepository.findById(projectId).get();
            ProjectInfoResponse response = new ProjectInfoResponse();
            response.setId(project.getId());
            response.setName(project.getName());
            response.setDescription(project.getDescription());
            response.setStartDate(project.getStartDate());
            response.setEndDate(project.getEndDate());
            list.add(response);
        }
        return list;
    }
}
