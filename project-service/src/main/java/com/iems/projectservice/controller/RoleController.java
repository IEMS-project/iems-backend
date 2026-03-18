package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateRoleDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.Role;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/roles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Role & Permission")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @Operation(summary = "Create role")
    @RequireProjectPermission(ProjectPermission.ROLE_CREATE)
    public ResponseEntity<ApiResponseDto<Role>> createRole(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateRoleDto dto) {
        try {
            Role role = roleService.createRole(projectId, dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Role created successfully", role));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PatchMapping("/{roleId}")
    @Operation(summary = "Update role")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Role>> updateRole(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @Valid @RequestBody CreateRoleDto dto) {
        try {
            Role role = roleService.updateRole(roleId, dto);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Role updated successfully", role));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete role")
    @RequireProjectPermission(ProjectPermission.ROLE_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteRole(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId) {
        try {
            roleService.deleteRole(roleId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Role deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping
    @Operation(summary = "Get project roles")
    @RequireProjectPermission(ProjectPermission.ROLE_READ)
    public ResponseEntity<ApiResponseDto<List<Role>>> getRoles(@PathVariable UUID projectId) {
        try {
            List<Role> roles = roleService.getRolesByProject(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Roles retrieved successfully", roles));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{roleId}/permissions/{permission}")
    @Operation(summary = "Assign permission to role")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> assignPermission(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @PathVariable ProjectPermission permission) {
        try {
            roleService.assignPermission(roleId, permission);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permission assigned successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{roleId}/permissions/{permission}")
    @Operation(summary = "Remove permission from role")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> removePermission(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @PathVariable ProjectPermission permission) {
        try {
            roleService.removePermission(roleId, permission);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permission removed successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "Get role permissions")
    @RequireProjectPermission(ProjectPermission.ROLE_READ)
    public ResponseEntity<ApiResponseDto<List<ProjectPermission>>> getRolePermissions(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId) {
        try {
            List<ProjectPermission> perms = roleService.getRolePermissions(roleId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permissions retrieved successfully", perms));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping("/permissions/all")
    @Operation(summary = "Get all available permissions")
    @RequireProjectPermission(ProjectPermission.ROLE_READ)
    public ResponseEntity<ApiResponseDto<List<ProjectPermission>>> getAllPermissions(
            @PathVariable UUID projectId) {
        try {
            List<ProjectPermission> perms = roleService.getAllPermissions();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permissions retrieved successfully", perms));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
