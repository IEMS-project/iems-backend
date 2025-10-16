package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.PermissionRepository;
import com.iems.iamservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Create new role
     */
    @Transactional
    public Role create(CreateRoleDto dto) {
        log.info("Creating new role: {}", dto.getCode());

        if (roleRepository.existsByCode(dto.getCode())) {
            throw new AppException(ErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }

        Role role = Role.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .build();

        // Set permissions after building to ensure proper bidirectional relationship
        Set<Permission> permissions = fetchPermissions(dto.getPermissionCodes());
        permissions.forEach(permission -> {
            role.getPermissions().add(permission);
            permission.getRoles().add(role);
        });

        Role savedRole = roleRepository.save(role);
        log.info("Role created successfully: {} with ID: {}", savedRole.getCode(), savedRole.getId());
        return savedRole;
    }

    /**
     * Get all roles
     */
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    /**
     * Find role by ID
     */
    public Role findById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND_BY_ID));
    }

    /**
     * Find role by code
     */
    public Optional<Role> findByCode(String code) {
        return roleRepository.findByCode(code);
    }

    /**
     * Update role
     */
    @Transactional
    public Role update(UUID id, UpdateRoleDto dto) {
        log.info("Updating role with ID: {}", id);

        try {
            Role role = findById(id);

            if (dto.getName() != null) {
                role.setName(dto.getName());
            }

            if (dto.getDescription() != null) {
                role.setDescription(dto.getDescription());
            }

            if (dto.getPermissionCodes() != null) {
                // Clear existing permissions from both sides of the relationship
                role.getPermissions().forEach(permission -> permission.getRoles().remove(role));
                role.getPermissions().clear();

                // Add new permissions
                Set<Permission> newPermissions = fetchPermissions(dto.getPermissionCodes());
                newPermissions.forEach(permission -> {
                    role.getPermissions().add(permission);
                    permission.getRoles().add(role);
                });
            }

            Role updatedRole = roleRepository.save(role);
            log.info("Role updated successfully: {}", updatedRole.getCode());
            return updatedRole;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update role with ID: {}", id, e);
            throw new AppException(ErrorCode.ROLE_UPDATE_FAILED);
        }
    }

    /**
     * Assign permissions to role
     */
    @Transactional
    public Role assignPermissions(UUID roleId, Set<String> permissionCodes) {
        log.info("Assigning permissions {} to role ID: {}", permissionCodes, roleId);

        try {
            Role role = findById(roleId);
            Set<Permission> permissions = fetchPermissions(permissionCodes);

            // Clear existing permissions from both sides of the relationship
            role.getPermissions().forEach(permission -> permission.getRoles().remove(role));
            role.getPermissions().clear();

            // Add new permissions
            permissions.forEach(permission -> {
                role.getPermissions().add(permission);
                permission.getRoles().add(role);
            });

            Role updatedRole = roleRepository.save(role);
            log.info("Permissions assigned successfully to role: {}", updatedRole.getCode());
            return updatedRole;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to assign permissions to role with ID: {}", roleId, e);
            throw new AppException(ErrorCode.ROLE_PERMISSION_ASSIGNMENT_FAILED);
        }
    }

    /**
     * Delete role
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting role with ID: {}", id);

        try {
            Role role = findById(id);

            // Check if role is in use (this should be checked through UserRolePermissionService)
            // For now, we'll just log a warning and proceed
            log.warn("Deleting role {} - ensure no users are assigned to this role", role.getCode());

            // Clear all permission relationships before deletion
            role.getPermissions().forEach(permission -> permission.getRoles().remove(role));
            role.getPermissions().clear();

            roleRepository.delete(role);
            log.info("Role deleted successfully: {}", role.getCode());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete role with ID: {}", id, e);
            throw new AppException(ErrorCode.ROLE_DELETE_FAILED);
        }
    }

    /**
     * Get permissions from codes
     */
    private Set<Permission> fetchPermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND_BY_CODE)))
                .collect(Collectors.toSet());
    }

    /**
     * Convert Role entity to RoleResponseDto
     */
    public RoleResponseDto toRoleResponse(Role role) {
        RoleResponseDto dto = new RoleResponseDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        Set<String> permissions = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        dto.setPermissions(permissions);
        return dto;
    }
}


