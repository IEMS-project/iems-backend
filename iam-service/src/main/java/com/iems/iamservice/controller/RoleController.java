package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.service.RoleService;
import com.iems.iamservice.service.UserRolePermissionService;
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
    private final UserRolePermissionService userRolePermissionService;

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

    /**
     * Get user IDs by role codes
     */
    @GetMapping("/users-by-roles")
    @Operation(summary = "Get users by roles", description = "Get list of user IDs that have any of the specified roles")
    public ResponseEntity<ApiResponseDto<List<UUID>>> getUserIdsByRoleCodes(@RequestParam List<String> roleCodes) {
        log.info("Getting user IDs for role codes: {}", roleCodes);
        
        List<UUID> userIds = userRolePermissionService.getUserIdsByRoleCodes(roleCodes);
        
        return ResponseEntity.ok(ApiResponseDto.<List<UUID>>builder()
                .status("success")
                .message("User IDs retrieved successfully")
                .data(userIds)
                .build());
    }
}


