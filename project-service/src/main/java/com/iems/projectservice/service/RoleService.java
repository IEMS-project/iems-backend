package com.iems.projectservice.service;

import com.iems.projectservice.dto.request.CreateRoleDto;
import com.iems.projectservice.entity.Permission;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.RolePermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.exception.ProjectErrorCode;
import com.iems.projectservice.repository.PermissionRepository;
import com.iems.projectservice.repository.RolePermissionRepository;
import com.iems.projectservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {
    
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    
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
        if (dto.getName() != null) role.setName(dto.getName());
        if (dto.getDescription() != null) role.setDescription(dto.getDescription());
        if (dto.getIsDefault() != null) role.setIsDefault(dto.getIsDefault());
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
    
    public RolePermission assignPermission(UUID roleId, UUID permissionId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.ROLE_NOT_FOUND));
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(ProjectErrorCode.PERMISSION_NOT_FOUND));
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new AppException(ProjectErrorCode.PERMISSION_ALREADY_ASSIGNED);
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        return rolePermissionRepository.save(rp);
    }
    
    @Transactional
    public void removePermission(UUID roleId, UUID permissionId) {
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
    }
    
    public List<RolePermission> getRolePermissions(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId);
    }

    // --- Permission CRUD ---
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }
    
    public Permission createPermission(String code, String description) {
        if (permissionRepository.existsByCode(code)) {
            throw new AppException(ProjectErrorCode.INVALID_REQUEST);
        }
        Permission p = new Permission();
        p.setCode(code);
        p.setDescription(description);
        return permissionRepository.save(p);
    }
}
