package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
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
            throw new AppException(ErrorCode.PERMISSION_CODE_ALREADY_EXISTS);
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
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND_BY_ID));
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

        try {
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
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update permission with ID: {}", id, e);
            throw new AppException(ErrorCode.PERMISSION_UPDATE_FAILED);
        }
    }

    /**
     * Delete permission
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting permission with ID: {}", id);

        try {
            Permission permission = findById(id);
            
            // Check if permission is in use
            if (!permission.getRoles().isEmpty()) {
                throw new AppException(ErrorCode.PERMISSION_IN_USE);
            }
            
            // Clear all role relationships before deletion
            permission.getRoles().forEach(role -> role.getPermissions().remove(permission));
            permission.getRoles().clear();

            permissionRepository.delete(permission);
            log.info("Permission deleted successfully: {}", permission.getCode());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete permission with ID: {}", id, e);
            throw new AppException(ErrorCode.PERMISSION_DELETE_FAILED);
        }
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


