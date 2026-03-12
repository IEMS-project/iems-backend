package com.iems.iamservice.controller;



import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.*;
import com.iems.iamservice.dto.response.AccountResponseDto;
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

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing user accounts and permissions")
public class AccountController {

    private final AccountService accountService;
    private final UserRolePermissionService userRolePermissionService;

    @PostMapping
    @Operation(summary = "Create user", description = "Create new user account")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> create(@Valid @RequestBody CreateAccountDto dto) {
        log.info("Creating user: {}", dto.getUsername());
        
        var created = accountService.createUser(dto);
        var response = accountService.toUserResponse(created);
        
        return ResponseEntity.created(URI.create("/api/users/" + created.getId()))
                .body(ApiResponseDto.<AccountResponseDto>builder()
                        .status("success")
                        .message("User created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping
    @Operation(summary = "List users", description = "Get list of all users")
    public ResponseEntity<ApiResponseDto<List<AccountResponseDto>>> list() {
        log.info("Getting all users");
        
        var data = accountService.findAll().stream()
                .map(accountService::toUserResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponseDto.<List<AccountResponseDto>>builder()
                .status("success")
                .message("Users list retrieved successfully")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "User details", description = "Get detailed user information by ID")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> get(@PathVariable UUID id) {
        log.info("Getting user with ID: {}", id);
        
        var user = accountService.findById(id);
        var response = accountService.toUserResponse(user);
        
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("User information retrieved successfully")
                .data(response)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update user information")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> update(@PathVariable UUID id, @Valid @RequestBody UpdateAccountDto dto) {
        log.info("Updating user with ID: {}", id);
        
        var updated = accountService.update(id, dto);
        var response = accountService.toUserResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("User updated successfully")
                .data(response)
                .build());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Reset user password", description = "Reset password for a user account")
    public ResponseEntity<ApiResponseDto<Void>> resetPassword(@PathVariable UUID id,
                                                              @Valid @RequestBody ResetPasswordRequestDto dto) {
        log.info("Resetting password for user ID: {}", id);

        accountService.resetPassword(id, dto.getNewPassword());

        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Password reset successfully")
                .build());
    }

    @PutMapping("/{id}/lock")
    @Operation(summary = "Lock/Unlock user", description = "Lock or unlock user account")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> lockUser(@PathVariable UUID id, @Valid @RequestBody LockUserRequestDto dto) {
        log.info("{} user with ID: {}", dto.getLocked() ? "Locking" : "Unlocking", id);
        
        var updated = accountService.lockUser(id, dto.getLocked(), dto.getReason());
        var response = accountService.toUserResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
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


