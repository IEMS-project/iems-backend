package com.iems.userservice.service;

import com.iems.userservice.dto.request.CreateRoleDto;
import com.iems.userservice.dto.request.UpdateRoleDto;
import com.iems.userservice.dto.response.RoleDto;
import com.iems.userservice.entity.Role;
import com.iems.userservice.exception.AppException;
import com.iems.userservice.exception.UserErrorCode;
import com.iems.userservice.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoleService {
    @Autowired
    private RoleRepository repository;

    public RoleDto createRole(CreateRoleDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) throw new AppException(UserErrorCode.INVALID_REQUEST);
        // check duplicate name
        if (repository.existsByName(dto.getName())) {
            throw new AppException(UserErrorCode.ROLE_NAME_ALREADY_EXISTS);
        }
        Role role = new Role();
        role.setName(dto.getName());

        Role saved = repository.save(role);
        return convertToDto(saved);
    }

    public RoleDto updateRole(UUID id, UpdateRoleDto dto) {
        Optional<Role> opt = repository.findById(id);
        if (opt.isEmpty()) throw new AppException(UserErrorCode.ROLE_NOT_FOUND);
        Role role = opt.get();
        if (dto.getName() != null) {
            // if another role has this name, forbid
            Optional<Role> byName = repository.findByName(dto.getName());
            if (byName.isPresent() && !byName.get().getId().equals(id)) {
                throw new AppException(UserErrorCode.ROLE_NAME_ALREADY_EXISTS);
            }
            role.setName(dto.getName());
        }
        Role saved = repository.save(role);
        return convertToDto(saved);
    }

    public void deleteRole(UUID id) {
        if (!repository.existsById(id)) throw new AppException(UserErrorCode.ROLE_NOT_FOUND);
        repository.deleteById(id);
    }

    public List<RoleDto> getAllRoles() {
        return repository.findAll().stream().map(this::convertToDto).toList();
    }

    public Optional<RoleDto> getRoleById(UUID id) {
        return repository.findById(id).map(this::convertToDto);
    }

    private RoleDto convertToDto(Role r) {
        if (r == null) return null;
        return new RoleDto(r.getId(), r.getName(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
