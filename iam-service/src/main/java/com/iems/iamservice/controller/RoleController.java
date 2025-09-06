package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.request.AssignPermissionRequestDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.dto.response.RolePermissionsResponseDto;
import com.iems.iamservice.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Role Management", description = "APIs for managing roles and permissions")
public class RoleController {

    private final RoleService roleService;

    /**
     * Create new role
     */
    @PostMapping
    @Operation(summary = "Create role", description = "Create new role")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> create(@Valid @RequestBody CreateRoleDto dto) {
        log.info("Creating role: {}", dto.getCode());
        
        var created = roleService.create(dto);
        var response = roleService.toRoleResponse(created);
        
        return ResponseEntity.created(URI.create("/api/roles/" + created.getId()))
                .body(ApiResponseDto.<RoleResponseDto>builder()
                        .status("success")
                        .message("Role created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Get list of roles
     */
    @GetMapping
    @Operation(summary = "List roles", description = "Get list of all roles")
    public ResponseEntity<ApiResponseDto<List<RoleResponseDto>>> list() {
        log.info("Getting all roles");
        
        var data = roleService.findAll().stream()
                .map(roleService::toRoleResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponseDto.<List<RoleResponseDto>>builder()
                .status("success")
                .message("Roles list retrieved successfully")
                .data(data)
                .build());
    }

    /**
     * Get role information by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Role details", description = "Get detailed role information by ID")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> get(@PathVariable UUID id) {
        log.info("Getting role with ID: {}", id);
        
        var role = roleService.findById(id);
        var response = roleService.toRoleResponse(role);
        
        return ResponseEntity.ok(ApiResponseDto.<RoleResponseDto>builder()
                .status("success")
                .message("Role information retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Update role
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update role", description = "Update role information")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleDto dto) {
        log.info("Updating role with ID: {}", id);
        
        var updated = roleService.update(id, dto);
        var response = roleService.toRoleResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<RoleResponseDto>builder()
                .status("success")
                .message("Role updated successfully")
                .data(response)
                .build());
    }

    /**
     * Assign permissions to role
     */
    @PostMapping("/{id}/permissions")
    @Operation(summary = "Assign permissions to role", description = "Assign list of permissions to role")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> assignPermissions(@PathVariable UUID id, @Valid @RequestBody AssignPermissionRequestDto dto) {
        log.info("Assigning permissions {} to role ID: {}", dto.getPermissionCodes(), id);
        
        var updated = roleService.assignPermissions(id, dto.getPermissionCodes());
        var response = roleService.toRoleResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<RoleResponseDto>builder()
                .status("success")
                .message("Permissions assigned to role successfully")
                .data(response)
                .build());
    }

    /**
     * Get list of role permissions
     */
    @GetMapping("/{id}/permissions")
    @Operation(summary = "Role permissions", description = "Get list of role permissions")
    public ResponseEntity<ApiResponseDto<RolePermissionsResponseDto>> getRolePermissions(@PathVariable UUID id) {
        log.info("Getting permissions for role ID: {}", id);
        
        var role = roleService.findById(id);
        
        var response = RolePermissionsResponseDto.builder()
                .roleId(role.getId())
                .roleCode(role.getCode())
                .roleName(role.getName())
                .description(role.getDescription())
                .active(role.getActive())
                .createdAt(role.getCreatedAt())
                .permissions(role.getPermissions().stream()
                        .map(permission -> permission.getCode())
                        .collect(Collectors.toSet()))
                .build();
        
        return ResponseEntity.ok(ApiResponseDto.<RolePermissionsResponseDto>builder()
                .status("success")
                .message("Role permissions retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Delete role
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role", description = "Delete role")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID id) {
        log.info("Deleting role with ID: {}", id);
        
        roleService.delete(id);
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Role deleted successfully")
                .build());
    }
}


