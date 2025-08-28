package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.entity.Role;
import com.iems.iamservice.repository.PermissionRepository;
import com.iems.iamservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public Role create(CreateRoleDto dto) {
        if (roleRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Role code already exists");
        }
        Role role = Role.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .permissions(fetchPermissions(dto.getPermissionCodes()))
                .build();
        return roleRepository.save(role);
    }

    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    public Role findById(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
    }

    @Transactional
    public Role update(Long id, UpdateRoleDto dto) {
        Role role = findById(id);
        if (dto.getName() != null) {
            role.setName(dto.getName());
        }
        if (dto.getPermissionCodes() != null) {
            role.setPermissions(fetchPermissions(dto.getPermissionCodes()));
        }
        return roleRepository.save(role);
    }

    @Transactional
    public void delete(Long id) {
        roleRepository.deleteById(id);
    }

    private Set<Permission> fetchPermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + code)))
                .collect(Collectors.toSet());
    }
}


