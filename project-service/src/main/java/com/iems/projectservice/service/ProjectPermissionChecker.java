package com.iems.projectservice.service;

import com.iems.projectservice.entity.MemberPermission;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.enums.MemberStatus;
import com.iems.projectservice.entity.enums.PermissionGrantType;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.MemberPermissionRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.RoleRepository;
import com.iems.projectservice.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates whether a project member has a given permission.
 *
 * Resolution order (same as Jira):
 *   1. Direct MemberPermission override DENY  → denied
 *   2. Direct MemberPermission override GRANT → granted
 *   3. RolePermission for the member's role   → granted if present
 *   4. Otherwise                              → denied
 */
@Service
@RequiredArgsConstructor
public class ProjectPermissionChecker {

    private final ProjectMemberRepository projectMemberRepository;
    private final MemberPermissionRepository memberPermissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public boolean hasPermission(UUID projectId, UUID accountId, ProjectPermission permission) {
        // 1 — membership check first
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndAccountId(projectId, accountId)
                .orElse(null);

        if (member == null || member.getStatus() != MemberStatus.ACTIVE) {
            return false;
        }

        // Default/admin role always has full project permissions.
        Role role = roleRepository.findById(member.getRoleId()).orElse(null);
        if (role != null && Boolean.TRUE.equals(role.getIsDefault())) {
            return true;
        }

        // 2 & 3 — direct overrides and role-based grants
        for (ProjectPermission candidate : resolvePermissionCandidates(permission)) {
            Optional<MemberPermission> override =
                    memberPermissionRepository.findByProjectIdAndAccountIdAndPermission(projectId, accountId, candidate);
            if (override.isPresent()) {
                return override.get().getType() == PermissionGrantType.GRANT;
            }

            if (rolePermissionRepository.existsByRoleIdAndPermission(member.getRoleId(), candidate)) {
                return true;
            }
        }

        return false;
    }

    private List<ProjectPermission> resolvePermissionCandidates(ProjectPermission requested) {
        return switch (requested) {
            case PROJECT_READ -> Arrays.asList(
                    ProjectPermission.PROJECT_READ,
                    ProjectPermission.PROJECT_CREATE,
                    ProjectPermission.PROJECT_UPDATE,
                    ProjectPermission.PROJECT_DELETE);
                case PROJECT_UPDATE -> Arrays.asList(
                    ProjectPermission.PROJECT_UPDATE);
            case PROJECT_DELETE -> Arrays.asList(
                    ProjectPermission.PROJECT_DELETE);
            case ISSUE_READ -> Arrays.asList(
                    ProjectPermission.ISSUE_READ,
                    ProjectPermission.ISSUE_CREATE,
                    ProjectPermission.ISSUE_UPDATE,
                    ProjectPermission.ISSUE_DELETE);
            case ISSUE_UPDATE -> Arrays.asList(
                    ProjectPermission.ISSUE_UPDATE);
                case WORKFLOW_READ -> Arrays.asList(
                    ProjectPermission.WORKFLOW_READ,
                    ProjectPermission.WORKFLOW_CREATE,
                    ProjectPermission.WORKFLOW_UPDATE,
                    ProjectPermission.WORKFLOW_DELETE);
                case WORKFLOW_CREATE -> Arrays.asList(
                    ProjectPermission.WORKFLOW_CREATE);
                case WORKFLOW_UPDATE -> Arrays.asList(
                    ProjectPermission.WORKFLOW_UPDATE);
                case WORKFLOW_DELETE -> Arrays.asList(
                    ProjectPermission.WORKFLOW_DELETE);
                case ROLE_READ -> Arrays.asList(
                    ProjectPermission.ROLE_READ,
                    ProjectPermission.ROLE_CREATE,
                    ProjectPermission.ROLE_UPDATE,
                    ProjectPermission.ROLE_DELETE);
                case ROLE_CREATE -> Arrays.asList(
                    ProjectPermission.ROLE_CREATE);
                case ROLE_UPDATE -> Arrays.asList(
                    ProjectPermission.ROLE_UPDATE);
                case ROLE_DELETE -> Arrays.asList(
                    ProjectPermission.ROLE_DELETE);
                    case SPRINT_READ -> Arrays.asList(
                        ProjectPermission.SPRINT_READ,
                        ProjectPermission.SPRINT_CREATE,
                        ProjectPermission.SPRINT_UPDATE,
                        ProjectPermission.SPRINT_DELETE);
                    case SPRINT_CREATE -> Arrays.asList(
                        ProjectPermission.SPRINT_CREATE);
                    case SPRINT_UPDATE -> Arrays.asList(
                        ProjectPermission.SPRINT_UPDATE);
                    case SPRINT_DELETE -> Arrays.asList(
                        ProjectPermission.SPRINT_DELETE);
            default -> List.of(requested);
        };
    }

    /** Throws PERMISSION_DENIED (HTTP 403) when the member lacks the permission. */
    public void requirePermission(UUID projectId, UUID accountId, ProjectPermission permission) {
        if (!hasPermission(projectId, accountId, permission)) {
            throw new AppException(ProjectErrorCode.PERMISSION_DENIED);
        }
    }
}
