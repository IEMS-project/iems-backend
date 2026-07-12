package com.iems.projectservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.projectservice.client.UserServiceFeignClient;
import com.iems.projectservice.dto.request.AccountIdsDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.dto.response.UserDetailDto;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.enums.MemberStatus;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.RoleRepository;
import com.iems.projectservice.repository.MemberPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final RoleRepository roleRepository;
    private final MemberPermissionRepository memberPermissionRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final SubscriptionLimitService subscriptionLimitService;
    private final NotificationPublisher notificationPublisher;
    private final ActorNameResolver actorNameResolver;

    /**
     * Adds project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param roleId the role id parameter
     * @return the add member to project result
     */
    @Transactional
    public ProjectMember addMemberToProject(UUID projectId, UUID accountId, UUID roleId) {
        validateAccountsForInvitation(Set.of(accountId), projectId);
        // Check member limit based on project owner's subscription
        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        long memberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
        subscriptionLimitService.checkCanAddMember(memberCount, ownerSub);

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setRoleId(roleId);
        member.setJoinedAt(LocalDateTime.now());
        member.setAssignedByAccountId(accountId); // will be overridden by caller if needed
        return projectMemberRepository.save(member);
    }

    /**
     * Adds project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param roleId the role id parameter
     * @param assignedBy the assigned by parameter
     * @return the add member to project result
     */
    @Transactional
    public ProjectMember addMemberToProject(UUID projectId, UUID accountId, UUID roleId, UUID assignedBy) {
        validateAccountsForInvitation(Set.of(accountId), projectId);
        // Check member limit (skip for initial project creation where member == owner)
        if (!accountId.equals(assignedBy)) {
            String ownerSub = projectRepository.findById(projectId)
                    .map(p -> p.getOwnerSubscription()).orElse("FREE");
            long memberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);
            subscriptionLimitService.checkCanAddMember(memberCount, ownerSub);
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setRoleId(roleId);
        member.setJoinedAt(LocalDateTime.now());
        member.setAssignedByAccountId(assignedBy);
        ProjectMember saved = projectMemberRepository.save(member);

        // Notify added member (skip if adding yourself)
        try {
            var project = projectRepository.findById(projectId).orElse(null);
            String projectName = project != null ? project.getName() : "";
            String actorName = actorNameResolver.resolve(assignedBy);
            notificationPublisher.notifyMemberAdded(accountId, assignedBy, actorName, projectId, projectName);
        } catch (Exception e) {
            log.warn("Failed to send member added notification: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Adds project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountIds the account ids parameter
     * @param roleId the role id parameter
     * @param assignedBy the assigned by parameter
     * @return the matching result collection
     */
    @Transactional
    public List<ProjectMember> addMembersToProject(UUID projectId, List<UUID> accountIds, UUID roleId,
            UUID assignedBy) {
        Set<UUID> targetIds = accountIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        validateAccountsForInvitation(targetIds, projectId);

        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        long memberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);

        List<ProjectMember> created = new ArrayList<>();
        for (UUID accountId : targetIds) {
            // Check limit before each insert (count grows as batch progresses)
            subscriptionLimitService.checkCanAddMember(memberCount, ownerSub);
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setAccountId(accountId);
            member.setRoleId(roleId);
            member.setJoinedAt(LocalDateTime.now());
            member.setAssignedByAccountId(assignedBy);
            created.add(projectMemberRepository.save(member));
            memberCount++;
        }
        return created;
    }

    /**
     * Adds project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param members the members parameter
     * @param assignedBy the assigned by parameter
     * @return the matching result collection
     */
    @Transactional
    public List<ProjectMember> addMembersToProject(UUID projectId,
            List<com.iems.projectservice.dto.request.ProjectMemberDto> members,
            UUID assignedBy) {
        Set<UUID> targetIds = members.stream()
                .filter(m -> m != null && m.getAccountId() != null && m.getRoleId() != null)
                .map(com.iems.projectservice.dto.request.ProjectMemberDto::getAccountId)
                .collect(Collectors.toSet());
        validateAccountsForInvitation(targetIds, projectId);

        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
        long memberCount = projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE);

        List<ProjectMember> created = new ArrayList<>();
        for (com.iems.projectservice.dto.request.ProjectMemberDto memberDto : members) {
            if (memberDto == null || memberDto.getAccountId() == null || memberDto.getRoleId() == null) continue;
            // Check limit before each insert (count grows as batch progresses)
            subscriptionLimitService.checkCanAddMember(memberCount, ownerSub);
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setAccountId(memberDto.getAccountId());
            member.setRoleId(memberDto.getRoleId());
            member.setJoinedAt(LocalDateTime.now());
            member.setAssignedByAccountId(assignedBy);
            created.add(projectMemberRepository.save(member));
            memberCount++;
        }
        return created;
    }

    /**
     * Removes project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     */
    @Transactional
    public void removeMember(UUID projectId, UUID accountId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        memberPermissionRepository.deleteByProjectIdAndAccountId(projectId, accountId);
        projectMemberRepository.delete(member);
    }

    /**
     * Updates project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param newRoleId the new role id parameter
     * @return the update member role result
     */
    @Transactional
    public ProjectMember updateMemberRole(UUID projectId, UUID accountId, UUID newRoleId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        member.setRoleId(newRoleId);
        return projectMemberRepository.save(member);
    }

    /**
     * Updates project member data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param status the status parameter
     * @return the update member status result
     */
    @Transactional
    public ProjectMember updateMemberStatus(UUID projectId, UUID accountId, MemberStatus status) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
        member.setStatus(status);
        return projectMemberRepository.save(member);
    }

    /**
     * Retrieves project member information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<ProjectMember> getProjectMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    /**
     * Returns is project member for project member processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    public boolean isProjectMember(UUID projectId, UUID accountId) {
        return projectMemberRepository.existsByProjectIdAndAccountIdAndStatus(projectId, accountId, MemberStatus.ACTIVE);
    }

    /**
     * Retrieves project member information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @return the get member result
     */
    public ProjectMember getMember(UUID projectId, UUID accountId) {
        return projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * Retrieves project member information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<ProjectMemberResponseDto> getProjectMembersEnriched(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        if (members.isEmpty())
            return List.of();

        // Collect all accountIds to batch-fetch (members + assignedBy)
        Set<UUID> accountIds = new HashSet<>();
        for (ProjectMember m : members) {
            accountIds.add(m.getAccountId());
            if (m.getAssignedByAccountId() != null) {
                accountIds.add(m.getAssignedByAccountId());
            }
        }

        // Batch-fetch users from IAM service: UserResponseDto.id == accountId
        Map<UUID, UserDetailDto> userMap = new HashMap<>();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(accountIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                List<UserDetailDto> users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
                userMap = users.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserDetailDto::getId, u -> u));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user details from IAM service: {}", e.getMessage());
        }

        // Fetch roles from local DB
        Set<UUID> roleIds = members.stream().map(ProjectMember::getRoleId).collect(Collectors.toSet());
        Map<UUID, String> roleNameMap = roleRepository.findAllById(roleIds)
                .stream().collect(Collectors.toMap(Role::getId, Role::getName));

        final Map<UUID, UserDetailDto> finalUserMap = userMap;
        return members.stream().map(m -> {
            UserDetailDto user = finalUserMap.get(m.getAccountId());
            UserDetailDto assignedBy = finalUserMap.get(m.getAssignedByAccountId());

            return new ProjectMemberResponseDto(
                    m.getId(),
                    m.getAccountId(),
                    user != null ? trim(user.getFirstName()) + " " + trim(user.getLastName()) : null,
                    user != null ? user.getEmail() : null,
                    user != null ? user.getImage() : null,
                    m.getRoleId(),
                    roleNameMap.get(m.getRoleId()),
                    m.getStatus(),
                    m.getJoinedAt(),
                    m.getAssignedByAccountId(),
                    assignedBy != null ? trim(assignedBy.getFirstName()) + " " + trim(assignedBy.getLastName()) : null);
        }).collect(Collectors.toList());
    }

    /**
     * Returns trim for project member processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param s the s parameter
     * @return the trim result
     */
    private String trim(String s) {
        return s != null ? s.trim() : "";
    }

    /**
     * Validates project member data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param accountIds the account ids parameter
     * @param projectId the project id parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void validateAccountsForInvitation(Set<UUID> accountIds, UUID projectId) {
        if (accountIds == null || accountIds.isEmpty()) return;

        List<UserDetailDto> users = new ArrayList<>();
        try {
            ResponseEntity<Map<String, Object>> response = userServiceFeignClient
                    .getUsersByAccountIds(new AccountIdsDto(accountIds));
            if (response.getBody() != null && response.getBody().get("data") != null) {
                users = objectMapper.convertValue(
                        response.getBody().get("data"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UserDetailDto.class));
            }
        } catch (Exception e) {
            log.error("Failed to fetch user details from iam-service: {}", e.getMessage());
            throw new AppException(ProjectErrorCode.INTERNAL_SERVER_ERROR);
        }

        Map<UUID, UserDetailDto> userMap = users.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(UserDetailDto::getId, u -> u));

        for (UUID accountId : accountIds) {
            if (!userMap.containsKey(accountId)) {
                throw new AppException(ProjectErrorCode.USER_NOT_FOUND);
            }
            UserDetailDto user = userMap.get(accountId);
            if (Boolean.FALSE.equals(user.getEnabled())) {
                throw new AppException(ProjectErrorCode.USER_LOCKED);
            }
            if (projectMemberRepository.existsByProjectIdAndAccountId(projectId, accountId)) {
                throw new AppException(ProjectErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
            }
        }
    }
}
