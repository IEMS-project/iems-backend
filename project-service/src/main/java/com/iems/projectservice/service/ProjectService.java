package com.iems.projectservice.service;

import com.iems.projectservice.client.DocumentServiceFeignClient;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.request.CreateProjectDto;
import com.iems.projectservice.dto.request.UpdateProjectDto;
import com.iems.projectservice.dto.response.*;
import com.iems.projectservice.entity.Project;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.Workflow;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.entity.enums.ProjectStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.*;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberPermissionRepository memberPermissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final IssueStatusHistoryRepository issueStatusHistoryRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final IssuePriorityRepository issuePriorityRepository;
    private final SprintRepository sprintRepository;
    private final LabelRepository labelRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final ProjectMemberService projectMemberService;
    private final RoleService roleService;
    private final WorkflowService workflowService;
    private final IssueService issueService;
    private final DocumentServiceFeignClient documentServiceFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;
    private final SubscriptionLimitService subscriptionLimitService;
    private final ProjectSubscriptionSyncService projectSubscriptionSyncService;

    public UUID getUserIdFromRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @Transactional
    public Project createProject(CreateProjectDto dto) {
        log.info("Creating project: {}", dto.getName());
        final String authHeader = extractAuthorizationHeader();
        final long startMs = System.currentTimeMillis();

        // ── Subscription guard: limit how many projects a user can own ──────
        UUID currentUserId = getUserIdFromRequest();
        long ownedCount = projectRepository.countByManagerAccountId(currentUserId);
        subscriptionLimitService.checkCanCreateProject(ownedCount);
        // ────────────────────────────────────────────────────────────────────

        if (projectRepository.existsByName(dto.getName())) {
            throw new AppException(ProjectErrorCode.PROJECT_NAME_EXISTS);
        }
        if (projectRepository.existsByProjectKey(dto.getProjectKey())) {
            throw new AppException(ProjectErrorCode.PROJECT_KEY_EXISTS);
        }

        ProjectStatus status = dto.getStatus() != null ? dto.getStatus() : ProjectStatus.PLANNING;

        // Snapshot owner subscription so project-level limits don't need Feign calls later
        String ownerSubscription = subscriptionLimitService.getCurrentUserSubscription();

        Project project = new Project();
        project.setName(dto.getName());
        project.setProjectKey(dto.getProjectKey().toUpperCase());
        project.setDescription(dto.getDescription());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setManagerAccountId(currentUserId);
        project.setCreatedByAccountId(currentUserId);
        project.setStatus(status);
        project.setOwnerSubscription(ownerSubscription);

        Project savedProject = projectRepository.save(project);

        // Create default roles
        com.iems.projectservice.dto.request.CreateRoleDto adminRoleDto = new com.iems.projectservice.dto.request.CreateRoleDto();
        adminRoleDto.setName("Admin");
        adminRoleDto.setDescription("Project administrator with full access");
        adminRoleDto.setIsDefault(true);
        Role adminRole = roleService.createRoleSkipLimitCheck(savedProject.getId(), adminRoleDto);

        List<ProjectPermission> defaultPermissions = Arrays.stream(ProjectPermission.values())
                .filter(permission -> permission.name().startsWith("PROJECT_")
                        || permission.name().startsWith("ISSUE_")
                        || permission.name().startsWith("WORKFLOW_")
                        || permission.name().startsWith("ROLE_")
                        || permission.name().startsWith("SPRINT_")
                        || permission.name().startsWith("MEMBER_")
                        || permission.name().equals("DOCUMENT_VIEW")
                        || permission.name().equals("DOCUMENT_MODIFY"))
                .toList();
        roleService.assignInitialPermissionsForNewRole(adminRole.getId(), defaultPermissions);
        log.debug("createProject stage=role_setup durationMs={}", System.currentTimeMillis() - startMs);

        // Add creator as admin member
        projectMemberService.addMemberToProject(savedProject.getId(), currentUserId, adminRole.getId(), currentUserId);
        log.debug("createProject stage=member_added durationMs={}", System.currentTimeMillis() - startMs);

        // Create default workflow
        workflowService.createDefaultWorkflow(savedProject.getId());
        log.debug("createProject stage=workflow_default durationMs={}", System.currentTimeMillis() - startMs);

        // Create default issue types and priorities
        issueService.createDefaultIssueTypes(savedProject.getId());
        issueService.createDefaultPriorities(savedProject.getId());
        log.debug("createProject stage=issue_defaults durationMs={}", System.currentTimeMillis() - startMs);

        // Run docs initialization only after DB commit so downstream membership checks
        // can see the new project.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        documentServiceFeignClient.initDefaultDocsFolder(savedProject.getId(), authHeader);
                    } catch (Exception e) {
                        log.warn("Failed to initialize default docs folder for project {}: {}", savedProject.getId(),
                                e.getMessage());
                    }
                });
            }
        });

        log.info("createProject completed projectId={} durationMs={}", savedProject.getId(),
                System.currentTimeMillis() - startMs);

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

    @Transactional
    public Project updateProjectAvatar(UUID projectId, String avatarUrl) {
        UUID currentUserId = getUserIdFromRequest();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!hasPermissionToUpdateProject(project, currentUserId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        project.setAvatarUrl(avatarUrl);
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        UUID currentUserId = getUserIdFromRequest();
        if (!currentUserId.equals(project.getCreatedByAccountId())) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }

        List<UUID> issueIds = issueRepository.findIdsByProjectId(projectId);
        if (!issueIds.isEmpty()) {
            attachmentRepository.deleteByIssueIdIn(issueIds);
            commentRepository.deleteByIssueIdIn(issueIds);
        }
        issueStatusHistoryRepository.deleteByProjectId(projectId);
        issueRepository.deleteIssueLabelsByProjectId(projectId);
        issueRepository.deleteByProjectId(projectId);

        issueTypeRepository.deleteByProjectId(projectId);
        issuePriorityRepository.deleteByProjectId(projectId);
        labelRepository.deleteByProjectId(projectId);
        sprintRepository.deleteByProjectId(projectId);

        List<UUID> workflowIds = workflowRepository.findByProjectId(projectId)
                .stream()
                .map(Workflow::getId)
                .toList();
        if (!workflowIds.isEmpty()) {
            workflowTransitionRepository.deleteByWorkflowIdIn(workflowIds);
            workflowStatusRepository.deleteByWorkflowIdIn(workflowIds);
        }
        workflowRepository.deleteByProjectId(projectId);

        List<UUID> roleIds = roleRepository.findByProjectId(projectId)
                .stream()
                .map(Role::getId)
                .toList();
        if (!roleIds.isEmpty()) {
            rolePermissionRepository.deleteByRoleIdIn(roleIds);
        }
        memberPermissionRepository.deleteByProjectId(projectId);
        projectMemberRepository.deleteByProjectId(projectId);
        roleRepository.deleteByProjectId(projectId);

        projectRepositoryRepository.deleteByProjectId(projectId);
        activityLogRepository.deleteByProjectId(projectId);

        projectRepository.delete(project);
    }

    public Project getProjectById(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PROJECT_NOT_FOUND));
        project = projectSubscriptionSyncService.refreshProjectSubscription(project);
        if (project.isLocked()) {
            throw new AppException(ProjectErrorCode.PROJECT_LOCKED);
        }
        return project;
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
                    p.getAvatarUrl(),
                    p.getStatus(),
                    p.getManagerAccountId(),
                    managerName != null ? managerName.trim() : null,
                    manager != null ? manager.getEmail() : null,
                    manager != null ? manager.getImage() : null,
                    p.getStartDate(),
                    p.getEndDate());
        }).collect(Collectors.toList());
    }

    public PagedResponseDto<MyProjectResponseDto> getMyProjects(int page, int size) {
        UUID accountId = getUserIdFromRequest();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> result = projectRepository.findByOwnerOrMember(accountId, pageable);
        List<MyProjectResponseDto> content = enrichMyProjects(result.getContent());

        return new PagedResponseDto<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    private List<MyProjectResponseDto> enrichMyProjects(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        Set<UUID> managerIds = projects.stream()
                .map(Project::getManagerAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        if (!managerIds.isEmpty()) {
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
                log.warn("Failed to fetch manager details for my projects from IAM service: {}", e.getMessage());
            }
        }

        Map<UUID, UserDetailDto> finalUserMap = userMap;
        return projects.stream()
                .map(project -> toMyProjectResponse(project, finalUserMap.get(project.getManagerAccountId())))
                .collect(Collectors.toList());
    }

    private MyProjectResponseDto toMyProjectResponse(Project project, UserDetailDto manager) {
        String managerName = manager != null
                ? (Optional.ofNullable(manager.getFirstName()).orElse("").trim() + " "
                        + Optional.ofNullable(manager.getLastName()).orElse("").trim()).trim()
                : null;

        MyProjectResponseDto dto = new MyProjectResponseDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setAvatarUrl(project.getAvatarUrl());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setStatus(project.getStatus());
        dto.setCreatedBy(project.getCreatedByAccountId());
        dto.setManagerId(project.getManagerAccountId());
        dto.setManagerName(managerName);
        dto.setManagerEmail(manager != null ? manager.getEmail() : null);
        dto.setManagerImage(manager != null ? manager.getImage() : null);
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    private boolean hasPermissionToUpdateProject(Project project, UUID accountId) {
        return project.getManagerAccountId().equals(accountId) || isAdmin(accountId);
    }

    private String extractAuthorizationHeader() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes.getRequest().getHeader("Authorization");
            }
        } catch (Exception ignored) {
            // Ignore request context extraction errors.
        }
        return null;
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
