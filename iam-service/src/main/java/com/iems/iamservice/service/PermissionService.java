package com.iems.iamservice.service;

import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.entity.Permission;
import com.iems.iamservice.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional
    public Permission create(CreatePermissionDto dto) {
        if (permissionRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Permission code already exists");
        }
        Permission p = Permission.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .build();
        return permissionRepository.save(p);
    }

    public List<Permission> findAll() {
        return permissionRepository.findAll();
    }

    public Permission findById(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Permission not found"));
    }

    @Transactional
    public Permission update(Long id, UpdatePermissionDto dto) {
        Permission p = findById(id);
        if (dto.getName() != null) {
            p.setName(dto.getName());
        }
        return permissionRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        permissionRepository.deleteById(id);
    }
}


