package com.iems.projectservice.service;

import com.iems.projectservice.dto.response.MemberPermissionsResponseDto;
import com.iems.projectservice.entity.MemberPermission;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.enums.PermissionGrantType;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.MemberPermissionRepository;
import com.iems.projectservice.repository.ProjectMemberRepository;
import com.iems.projectservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberPermissionService {

    private final MemberPermissionRepository memberPermissionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final RoleRepository roleRepository;

    /**
     * Retrieves member permission information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @return the get member permissions result
     */
    public MemberPermissionsResponseDto getMemberPermissions(UUID projectId, UUID accountId) {
        validateMembership(projectId, accountId);

        List<MemberPermission> perms = memberPermissionRepository
                .findByProjectIdAndAccountId(projectId, accountId);

        List<String> granted = perms.stream()
                .filter(p -> p.getType() == PermissionGrantType.GRANT)
                .map(p -> p.getPermission().name())
                .collect(Collectors.toList());

        List<String> denied = perms.stream()
                .filter(p -> p.getType() == PermissionGrantType.DENY)
                .map(p -> p.getPermission().name())
                .collect(Collectors.toList());

        return new MemberPermissionsResponseDto(granted, denied);
    }

    /**
     * Performs grant permission for member permission processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void grantPermission(UUID projectId, UUID accountId, ProjectPermission permission) {
        validateMemberPermissionEditable(projectId, accountId);
        // Remove any existing record (could be a DENY) before inserting GRANT
        memberPermissionRepository.deleteByProjectIdAndAccountIdAndPermission(projectId, accountId, permission);

        MemberPermission mp = new MemberPermission();
        mp.setProjectId(projectId);
        mp.setAccountId(accountId);
        mp.setPermission(permission);
        mp.setType(PermissionGrantType.GRANT);
        memberPermissionRepository.save(mp);
    }

    /**
     * Performs deny permission for member permission processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void denyPermission(UUID projectId, UUID accountId, ProjectPermission permission) {
        validateMemberPermissionEditable(projectId, accountId);
        // Remove any existing record (could be a GRANT) before inserting DENY
        memberPermissionRepository.deleteByProjectIdAndAccountIdAndPermission(projectId, accountId, permission);

        MemberPermission mp = new MemberPermission();
        mp.setProjectId(projectId);
        mp.setAccountId(accountId);
        mp.setPermission(permission);
        mp.setType(PermissionGrantType.DENY);
        memberPermissionRepository.save(mp);
    }

    /**
     * Performs reset permission for member permission processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void resetPermission(UUID projectId, UUID accountId, ProjectPermission permission) {
        validateMemberPermissionEditable(projectId, accountId);
        memberPermissionRepository.deleteByProjectIdAndAccountIdAndPermission(projectId, accountId, permission);
    }

    /**
     * Performs reset all permissions for member permission processing.
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     */
    @Transactional
    public void resetAllPermissions(UUID projectId, UUID accountId) {
        validateMemberPermissionEditable(projectId, accountId);
        memberPermissionRepository.deleteByProjectIdAndAccountId(projectId, accountId);
    }

    /**
     * Validates member permission data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void validateMembership(UUID projectId, UUID accountId) {
        if (!projectMemberRepository.existsByProjectIdAndAccountId(projectId, accountId)) {
            throw new AppException(ProjectErrorCode.MEMBER_NOT_FOUND);
        }
    }

    /**
     * Validates member permission data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param accountId the account id parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void validateMemberPermissionEditable(UUID projectId, UUID accountId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndAccountId(projectId, accountId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.MEMBER_NOT_FOUND));

        Role role = roleRepository.findById(member.getRoleId())
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));

        if (Boolean.TRUE.equals(role.getIsDefault())) {
            throw new AppException(ProjectErrorCode.DEFAULT_ROLE_MEMBER_PERMISSIONS_LOCKED);
        }
    }
}
