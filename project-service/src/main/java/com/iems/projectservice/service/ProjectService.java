package com.iems.projectservice.service;

import com.iems.projectservice.client.DocumentServiceFeignClient;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.ProjectIdsDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.*;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.entity.enums.ProjectStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.security.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberService projectMemberService;
    private final RoleService roleService;
    private final WorkflowService workflowService;
    private final IssueService issueService;
    private final DocumentServiceFeignClient documentServiceFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @Transactional
    public Project createProject(CreateProjectDto dto) {
        log.info("Creating project: {}", dto.getName());

        if (projectRepository.existsByName(dto.getName())) {
            throw new AppException(ProjectErrorCode.PROJECT_NAME_EXISTS);
        }
        if (projectRepository.existsByProjectKey(dto.getProjectKey())) {
            throw new AppException(ProjectErrorCode.PROJECT_KEY_EXISTS);
        }

        UUID currentUserId = getUserIdFromRequest();

        ProjectStatus status = dto.getStatus() != null ? dto.getStatus() : ProjectStatus.PLANNING;

        Project project = new Project();
        project.setName(dto.getName());
        project.setProjectKey(dto.getProjectKey().toUpperCase());
        project.setDescription(dto.getDescription());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setManagerAccountId(currentUserId);
        project.setCreatedByAccountId(currentUserId);
        project.setStatus(status);

        Project savedProject = projectRepository.save(project);

        // Create default roles
        com.iems.projectservice.dto.request.CreateRoleDto adminRoleDto = new com.iems.projectservice.dto.request.CreateRoleDto();
        adminRoleDto.setName("Admin");
        adminRoleDto.setDescription("Project administrator with full access");
        adminRoleDto.setIsDefault(true);
        Role adminRole = roleService.createRole(savedProject.getId(), adminRoleDto);

        for (ProjectPermission permission : ProjectPermission.values()) {
            if (permission.name().startsWith("PROJECT_")
                    || permission.name().startsWith("ISSUE_")
                    || permission.name().startsWith("WORKFLOW_")
                    || permission.name().startsWith("ROLE_")
                    || permission.name().startsWith("SPRINT_")
                    || permission.name().startsWith("MEMBER_")) {
                roleService.assignInitialPermission(adminRole.getId(), permission);
            }
        }

        // Add creator as admin member
        projectMemberService.addMemberToProject(savedProject.getId(), currentUserId, adminRole.getId(), currentUserId);

        // Create default workflow
        workflowService.createDefaultWorkflow(savedProject.getId());

        // Create default issue types and priorities
        issueService.createDefaultIssueTypes(savedProject.getId());
        issueService.createDefaultPriorities(savedProject.getId());

        // Run docs initialization only after DB commit so downstream membership checks can see the new project.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    documentServiceFeignClient.initDefaultDocsFolder(savedProject.getId());
                } catch (Exception e) {
                    log.warn("Failed to initialize default docs folder for project {}: {}", savedProject.getId(), e.getMessage());
                }
            }
        });

        return savedProject;
    }

    @Transactional
    public Project updateProject(UUID projectId, UpdateProjectDto dto) {
        log.info("Updating project: {}", projectId);
        UUID currentUserId = getUserIdFromRequest();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!hasPermissionToUpdateProject(project, currentUserId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        if (dto.getName() != null) {
            if (!dto.getName().equals(project.getName()) && projectRepository.existsByName(dto.getName())) {
                throw new AppException(ProjectErrorCode.PROJECT_NAME_EXISTS);
            }
            project.setName(dto.getName());
        }
        if (dto.getDescription() != null)
            project.setDescription(dto.getDescription());
        if (dto.getStartDate() != null)
            project.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null)
            project.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null)
            project.setStatus(dto.getStatus());
        if (dto.getManagerId() != null)
            project.setManagerAccountId(dto.getManagerId());

        return projectRepository.save(project);
    }

    public void deleteProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectRepository.delete(project);
    }

    public Project getProjectById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public List<ProjectTableDto> getProjectsTable() {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty())
            return List.of();

        // Batch-fetch manager info for all unique managerAccountIds
        Set<UUID> managerIds = projects.stream()
                .map(Project::getManagerAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(managerIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                userMap = users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch manager details from IAM service: {}", e.getMessage());
        }

        final Map<UUID, UserDetailDto> finalUserMap = userMap;
        return projects.stream().map(p -> {
            UserDetailDto manager = finalUserMap.get(p.getManagerAccountId());
            String managerName = manager != null
                    ? (manager.getFirstName() != null ? manager.getFirstName().trim() : "") + " "
                            + (manager.getLastName() != null ? manager.getLastName().trim() : "")
                    : null;
            return new ProjectTableDto(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getStatus(),
                    p.getManagerAccountId(),
                    managerName != null ? managerName.trim() : null,
                    manager != null ? manager.getEmail() : null,
                    manager != null ? manager.getImage() : null,
                    p.getStartDate(),
                    p.getEndDate());
        }).collect(Collectors.toList());
    }

    public List<Project> getMyProjects() {
        UUID accountId = getUserIdFromRequest();
        return projectRepository.findByMemberAccountId(accountId);
    }

    public List<ProjectInfoResponse> getProjectsByID(ProjectIdsDto projectIds) {
        List<ProjectInfoResponse> list = new ArrayList<>();
        for (UUID projectId : projectIds.getIds()) {
            projectRepository.findById(projectId).ifPresent(project -> {
                ProjectInfoResponse response = new ProjectInfoResponse();
                response.setId(project.getId());
                response.setName(project.getName());
                response.setDescription(project.getDescription());
                response.setStartDate(project.getStartDate());
                response.setEndDate(project.getEndDate());
                list.add(response);
            });
        }
        return list;
    }

    private boolean hasPermissionToUpdateProject(Project project, UUID accountId) {
        return project.getManagerAccountId().equals(accountId) || isAdmin(accountId);
    }

    public boolean isAdmin(UUID accountId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails)) {
                return false;
            }
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            return userDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        } catch (Exception e) {
            log.error("Error checking admin status for accountId: {}", accountId, e);
            return false;
        }
    }

    // --- User info helpers (via Feign) ---
    public Optional<UserDetailDto> getUserById(UUID accountId) {
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient.getUserByAccountId(accountId);
            if (response.getBody() != null && response.getBody().containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get("data");
                return Optional.of(convertToUserDetailDto(userData));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching user by accountId {}: {}", accountId, e.getMessage());
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
                (String) userData.get("role"));
    }
}
