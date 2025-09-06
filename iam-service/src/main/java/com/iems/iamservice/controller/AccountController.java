package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.request.LockUserRequestDto;
import com.iems.iamservice.dto.request.AssignRoleRequestDto;
import com.iems.iamservice.dto.request.AssignPermissionRequestDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.dto.response.UserPermissionsResponseDto;
import com.iems.iamservice.dto.response.UserPermissionDetails;
import com.iems.iamservice.service.AccountService;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing user accounts and permissions")
public class AccountController {

    private final AccountService accountService;
    private final UserRolePermissionService userRolePermissionService;

    @PostMapping
    @Operation(summary = "Create user", description = "Create new user account")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> create(@Valid @RequestBody CreateUserDto dto) {
        log.info("Creating user: {}", dto.getUsername());
        
        var created = accountService.create(dto);
        var response = accountService.toUserResponse(created);
        
        return ResponseEntity.created(URI.create("/api/users/" + created.getId()))
                .body(ApiResponseDto.<UserResponseDto>builder()
                        .status("success")
                        .message("User created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping
    @Operation(summary = "List users", description = "Get list of all users")
    public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> list() {
        log.info("Getting all users");
        
        var data = accountService.findAll().stream()
                .map(accountService::toUserResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponseDto.<List<UserResponseDto>>builder()
                .status("success")
                .message("Users list retrieved successfully")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "User details", description = "Get detailed user information by ID")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> get(@PathVariable UUID id) {
        log.info("Getting user with ID: {}", id);
        
        var user = accountService.findById(id);
        var response = accountService.toUserResponse(user);
        
        return ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                .status("success")
                .message("User information retrieved successfully")
                .data(response)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update user information")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserDto dto) {
        log.info("Updating user with ID: {}", id);
        
        var updated = accountService.update(id, dto);
        var response = accountService.toUserResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                .status("success")
                .message("User updated successfully")
                .data(response)
                .build());
    }

    @PutMapping("/{id}/lock")
    @Operation(summary = "Lock/Unlock user", description = "Lock or unlock user account")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> lockUser(@PathVariable UUID id, @Valid @RequestBody LockUserRequestDto dto) {
        log.info("{} user with ID: {}", dto.getLocked() ? "Locking" : "Unlocking", id);
        
        var updated = accountService.lockUser(id, dto.getLocked(), dto.getReason());
        var response = accountService.toUserResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                .status("success")
                .message(dto.getLocked() ? "User locked successfully" : "User unlocked successfully")
                .data(response)
                .build());
    }


    @PostMapping("/{id}/roles")
    @Operation(summary = "Assign roles to user", description = "Add roles to user (additive, no error for duplicates)")
    public ResponseEntity<ApiResponseDto<Void>> assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequestDto dto) {
        log.info("Assigning roles {} to user ID: {}", dto.getRoleCodes(), id);
        
        userRolePermissionService.assignRolesToUser(id, dto.getRoleCodes());
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Roles assigned to user successfully")
                .build());
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Replace user roles", description = "Replace all roles for user (removes existing roles)")
    public ResponseEntity<ApiResponseDto<Void>> replaceRoles(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequestDto dto) {
        log.info("Replacing roles {} for user ID: {}", dto.getRoleCodes(), id);
        
        userRolePermissionService.replaceUserRoles(id, dto.getRoleCodes());
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("User roles replaced successfully")
                .build());
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "Assign permissions to user", description = "Add permissions to user (additive, no error for duplicates)")
    public ResponseEntity<ApiResponseDto<Void>> assignPermissions(@PathVariable UUID id, @Valid @RequestBody AssignPermissionRequestDto dto) {
        log.info("Assigning permissions {} to user ID: {}", dto.getPermissionCodes(), id);
        
        userRolePermissionService.assignPermissionsToUser(id, dto.getPermissionCodes());
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Permissions assigned to user successfully")
                .build());
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Replace user permissions", description = "Replace all direct permissions for user (removes existing permissions)")
    public ResponseEntity<ApiResponseDto<Void>> replacePermissions(@PathVariable UUID id, @Valid @RequestBody AssignPermissionRequestDto dto) {
        log.info("Replacing permissions {} for user ID: {}", dto.getPermissionCodes(), id);
        
        userRolePermissionService.replaceUserPermissions(id, dto.getPermissionCodes());
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("User permissions replaced successfully")
                .build());
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "User permissions", description = "Get all permissions for user")
    public ResponseEntity<ApiResponseDto<UserPermissionsResponseDto>> getUserPermissions(@PathVariable UUID id) {
        log.info("Getting permissions for user ID: {}", id);
        
        var user = accountService.findById(id);
        var allPermissions = userRolePermissionService.getAllUserPermissions(id);
        var userRoles = userRolePermissionService.getUserRoles(id);
        var directPermissions = userRolePermissionService.getUserDirectPermissions(id);
        
        var response = UserPermissionsResponseDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .directPermissions(directPermissions.stream()
                        .map(permission -> permission.getCode())
                        .collect(Collectors.toSet()))
                .rolePermissions(userRoles.stream()
                        .map(role -> UserPermissionsResponseDto.RolePermissionsDto.builder()
                                .roleCode(role.getCode())
                                .roleName(role.getName())
                                .permissions(role.getPermissions().stream()
                                        .map(permission -> permission.getCode())
                                        .collect(Collectors.toSet()))
                                .build())
                        .collect(Collectors.toSet()))
                .allPermissions(allPermissions.stream()
                        .map(permission -> permission.getCode())
                        .collect(Collectors.toSet()))
                .build();
        
        return ResponseEntity.ok(ApiResponseDto.<UserPermissionsResponseDto>builder()
                .status("success")
                .message("User permissions retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/{id}/permissions/details")
    @Operation(summary = "User permissions details", description = "Get detailed permissions for user with source information")
    public ResponseEntity<ApiResponseDto<UserPermissionDetails>> getUserPermissionDetails(@PathVariable UUID id) {
        log.info("Getting detailed permissions for user ID: {}", id);
        
        // Verify user exists
        accountService.findById(id);
        var permissionDetails = userRolePermissionService.getUserPermissionDetails(id);
        
        return ResponseEntity.ok(ApiResponseDto.<UserPermissionDetails>builder()
                .status("success")
                .message("User permission details retrieved successfully")
                .data(permissionDetails)
                .build());
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete user account")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID id) {
        log.info("Deleting user with ID: {}", id);
        
        accountService.delete(id);
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("User deleted successfully")
                .build());
    }
}


