package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateRoleDto;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.RolePermission;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.repository.RolePermissionRepository;
import com.iems.projectservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ProjectRepository projectRepository;
    private final SubscriptionLimitService subscriptionLimitService;

    /**
     * Creates role data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param dto the dto parameter
     * @return the create role result
     * @throws AppException if a business rule prevents the requested operation
     */
    public Role createRole(UUID projectId, CreateRoleDto dto) {
        String ownerSub = projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");

        // Count only non-default (custom) roles — the initial Admin role doesn't count
        long customRoleCount = roleRepository.findByProjectId(projectId).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDefault()))
                .count();
        subscriptionLimitService.checkCanCreateCustomRole(customRoleCount, ownerSub);

        if (roleRepository.existsByProjectIdAndName(projectId, dto.getName())) {
            throw new AppException(ProjectErrorCode.ROLE_ALREADY_EXISTS);
        }
        return buildAndSaveRole(projectId, dto);
    }

    /**
     * Internal init-only path — bypasses the subscription limit check.
     * Used by ProjectService when creating the default Admin role during project setup.
     */
    public Role createRoleSkipLimitCheck(UUID projectId, CreateRoleDto dto) {
        if (roleRepository.existsByProjectIdAndName(projectId, dto.getName())) {
            throw new AppException(ProjectErrorCode.ROLE_ALREADY_EXISTS);
        }
        return buildAndSaveRole(projectId, dto);
    }

    /**
     * Builds role data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param dto the dto parameter
     * @return the build and save role result
     */
    private Role buildAndSaveRole(UUID projectId, CreateRoleDto dto) {
        Role role = new Role();
        role.setProjectId(projectId);
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        return roleRepository.save(role);
    }

    /**
     * Updates role data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param dto the dto parameter
     * @return the update role result
     */
    public Role updateRole(UUID roleId, CreateRoleDto dto) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
        if (dto.getName() != null)
            role.setName(dto.getName());
        if (dto.getDescription() != null)
            role.setDescription(dto.getDescription());
        if (dto.getIsDefault() != null)
            role.setIsDefault(dto.getIsDefault());
        return roleRepository.save(role);
    }

    /**
     * Deletes role data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     */
    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
        rolePermissionRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
    }

    /**
     * Retrieves role information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @return the matching result collection
     */
    public List<Role> getRolesByProject(UUID projectId) {
        return roleRepository.findByProjectId(projectId);
    }

    /**
     * Retrieves role information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @return the get role by id result
     */
    public Role getRoleById(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
    }

    /**
     * Assigns role data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permission the permission parameter
     */
    public void assignPermission(UUID roleId, ProjectPermission permission) {
        Role role = getRoleById(roleId);
        assertPermissionsMutable(role);
        saveRolePermission(roleId, permission);
    }

    /**
     * Assigns role data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permission the permission parameter
     */
    public void assignInitialPermission(UUID roleId, ProjectPermission permission) {
        getRoleById(roleId);
        saveRolePermission(roleId, permission);
    }

    /**
     * Assigns role data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permissions the permissions parameter
     */
    @Transactional
    public void assignInitialPermissions(UUID roleId, List<ProjectPermission> permissions) {
        getRoleById(roleId);
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        Set<ProjectPermission> existing = rolePermissionRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toCollection(HashSet::new));

        List<RolePermission> toSave = permissions.stream()
                .filter(permission -> !existing.contains(permission))
                .map(permission -> {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(roleId);
                    rp.setPermission(permission);
                    return rp;
                })
                .collect(Collectors.toList());

        if (!toSave.isEmpty()) {
            rolePermissionRepository.saveAll(toSave);
        }
    }

    /**
     * Assigns role data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permissions the permissions parameter
     */
    @Transactional
    public void assignInitialPermissionsForNewRole(UUID roleId, List<ProjectPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        List<RolePermission> toSave = permissions.stream()
                .distinct()
                .map(permission -> {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(roleId);
                    rp.setPermission(permission);
                    return rp;
                })
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(toSave);
    }

    /**
     * Saves role data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permission the permission parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void saveRolePermission(UUID roleId, ProjectPermission permission) {
        if (rolePermissionRepository.existsByRoleIdAndPermission(roleId, permission)) {
            throw new AppException(ProjectErrorCode.PERMISSION_ALREADY_ASSIGNED);
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }

    /**
     * Removes role data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void removePermission(UUID roleId, ProjectPermission permission) {
        Role role = getRoleById(roleId);
        assertPermissionsMutable(role);
        rolePermissionRepository.deleteByRoleIdAndPermission(roleId, permission);
    }

    /**
     * Retrieves role information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param roleId the role id parameter
     * @return the matching result collection
     */
    public List<ProjectPermission> getRolePermissions(UUID roleId) {
        Role role = getRoleById(roleId);
        if (Boolean.TRUE.equals(role.getIsDefault())) {
            return getAllPermissions();
        }
        return rolePermissionRepository.findByRoleId(roleId)
                .stream().map(RolePermission::getPermission).collect(Collectors.toList());
    }

    /**
     * Retrieves role information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<ProjectPermission> getAllPermissions() {
        return Arrays.asList(ProjectPermission.values());
    }

    /**
     * Performs assert permissions mutable for role processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param role the role parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void assertPermissionsMutable(Role role) {
        if (Boolean.TRUE.equals(role.getIsDefault())) {
            throw new AppException(ProjectErrorCode.DEFAULT_ROLE_PERMISSIONS_LOCKED);
        }
    }
}
