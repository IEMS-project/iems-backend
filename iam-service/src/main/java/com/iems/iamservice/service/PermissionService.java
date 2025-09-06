package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /**
     * Create new permission
     */
    @Transactional
    public Permission create(CreatePermissionDto dto) {
        log.info("Creating new permission: {}", dto.getCode());

        if (permissionRepository.existsByCode(dto.getCode())) {
            throw new RuntimeException("Permission code already exists");
        }

        Permission permission = Permission.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .build();

        Permission savedPermission = permissionRepository.save(permission);
        log.info("Permission created successfully: {} with ID: {}", savedPermission.getCode(), savedPermission.getId());
        return savedPermission;
    }

    /**
     * Get all permissions
     */
    public List<Permission> findAll() {
        return permissionRepository.findAll();
    }

    /**
     * Find permission by ID
     */
    public Permission findById(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found with ID: " + id));
    }

    /**
     * Find permission by code
     */
    public Optional<Permission> findByCode(String code) {
        return permissionRepository.findByCode(code);
    }

    /**
     * Update permission
     */
    @Transactional
    public Permission update(UUID id, UpdatePermissionDto dto) {
        log.info("Updating permission with ID: {}", id);

        Permission permission = findById(id);
        
        if (dto.getName() != null) {
            permission.setName(dto.getName());
        }
        
        if (dto.getDescription() != null) {
            permission.setDescription(dto.getDescription());
        }

        Permission updatedPermission = permissionRepository.save(permission);
        log.info("Permission updated successfully: {}", updatedPermission.getCode());
        return updatedPermission;
    }

    /**
     * Delete permission
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting permission with ID: {}", id);

        Permission permission = findById(id);
        
        // Clear all role relationships before deletion
        permission.getRoles().forEach(role -> role.getPermissions().remove(permission));
        permission.getRoles().clear();
        
        // Note: Permission usage check should be done through UserRolePermissionService

        permissionRepository.delete(permission);
        log.info("Permission deleted successfully: {}", permission.getCode());
    }

    /**
     * Convert Permission entity to PermissionResponseDto
     */
    public PermissionResponseDto toPermissionResponse(Permission permission) {
        PermissionResponseDto dto = new PermissionResponseDto();
        dto.setId(permission.getId());
        dto.setCode(permission.getCode());
        dto.setName(permission.getName());
        return dto;
    }
}


