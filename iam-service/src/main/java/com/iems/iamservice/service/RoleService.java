package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.exception.AppException;
import com.iems.iamservice.exception.ErrorCode;
import com.iems.iamservice.repository.RoleRepository;
import com.iems.iamservice.repository.UserRoleRepository;
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
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

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
     * Delete role
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting role with ID: {}", id);

        try {
            Role role = findById(id);
            userRoleRepository.deleteByRoleId(role.getId());
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
     * Convert Role entity to RoleResponseDto
     */
    public RoleResponseDto toRoleResponse(Role role) {
        RoleResponseDto dto = new RoleResponseDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        return dto;
    }
}


