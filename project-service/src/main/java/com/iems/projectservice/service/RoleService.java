package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateRoleDto;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.RolePermission;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
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

    public Role createRole(UUID projectId, CreateRoleDto dto) {
        if (roleRepository.existsByProjectIdAndName(projectId, dto.getName())) {
            throw new AppException(ProjectErrorCode.ROLE_ALREADY_EXISTS);
        }
        Role role = new Role();
        role.setProjectId(projectId);
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        return roleRepository.save(role);
    }

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

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
        rolePermissionRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
    }

    public List<Role> getRolesByProject(UUID projectId) {
        return roleRepository.findByProjectId(projectId);
    }

    public Role getRoleById(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
    }

    public void assignPermission(UUID roleId, ProjectPermission permission) {
        Role role = getRoleById(roleId);
        assertPermissionsMutable(role);
        saveRolePermission(roleId, permission);
    }

    public void assignInitialPermission(UUID roleId, ProjectPermission permission) {
        getRoleById(roleId);
        saveRolePermission(roleId, permission);
    }

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

    private void saveRolePermission(UUID roleId, ProjectPermission permission) {
        if (rolePermissionRepository.existsByRoleIdAndPermission(roleId, permission)) {
            throw new AppException(ProjectErrorCode.PERMISSION_ALREADY_ASSIGNED);
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermission(permission);
        rolePermissionRepository.save(rp);
    }

    @Transactional
    public void removePermission(UUID roleId, ProjectPermission permission) {
        Role role = getRoleById(roleId);
        assertPermissionsMutable(role);
        rolePermissionRepository.deleteByRoleIdAndPermission(roleId, permission);
    }

    public List<ProjectPermission> getRolePermissions(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId)
                .stream().map(RolePermission::getPermission).collect(Collectors.toList());
    }

    public List<ProjectPermission> getAllPermissions() {
        return Arrays.asList(ProjectPermission.values());
    }

    private void assertPermissionsMutable(Role role) {
        if (Boolean.TRUE.equals(role.getIsDefault())) {
            throw new AppException(ProjectErrorCode.DEFAULT_ROLE_PERMISSIONS_LOCKED);
        }
    }
}
