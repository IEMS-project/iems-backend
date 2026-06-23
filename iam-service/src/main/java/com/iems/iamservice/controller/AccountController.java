package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.*;
import com.iems.iamservice.dto.response.AdminAccountResponseDto;
import com.iems.iamservice.dto.response.AccountResponseDto;
import com.iems.iamservice.entity.Account;
import com.iems.iamservice.service.AccountService;
import com.iems.iamservice.service.UserRolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        
        return ResponseEntity.created(URI.create("/api/accounts/" + created.getId()))
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

    @GetMapping("/_admin")
    @Operation(summary = "List admin accounts", description = "Get paginated accounts with profile/avatar fields for admin UI")
    public ResponseEntity<ApiResponseDto<Page<AdminAccountResponseDto>>> listAdminAccounts(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var data = accountService.searchAdminAccounts(q, pageable);

        return ResponseEntity.ok(ApiResponseDto.<Page<AdminAccountResponseDto>>builder()
                .status("success")
                .message("Admin accounts retrieved successfully")
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
    @Operation(summary = "Assign role to user", description = "Assign role to user (replaces existing role - user can only have 1 role)")
    public ResponseEntity<ApiResponseDto<Void>> assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequestDto dto) {
        log.info("Assigning role {} to user ID: {}", dto.getRoleCodes(), id);
        
        userRolePermissionService.assignRolesToUser(id, dto.getRoleCodes());
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Role assigned to user successfully")
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

    // ─── Subscription endpoints ───────────────────────────────────────────────

    @GetMapping("/me/subscription")
    @Operation(summary = "Get my subscription", description = "Get current user's subscription status")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> getMySubscription() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        log.info("Getting subscription for user: {}", username);

        Account account = accountService.findByUsernameOrEmail(username)
                .orElseThrow();
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("Subscription info retrieved successfully")
                .data(accountService.toUserResponse(account))
                .build());
    }

    @PostMapping("/me/upgrade")
    @Operation(summary = "Upgrade to Premium", description = "Upgrade current account to Premium subscription")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> upgradeToPremium(
            @Valid @RequestBody UpgradeSubscriptionDto dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        log.info("Upgrading to premium for user: {} for {} days", username, dto.getDurationDays());

        Account account = accountService.findByUsernameOrEmail(username).orElseThrow();
        Account upgraded = accountService.upgradeToPremium(account.getId(), dto.getDurationDays());

        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("Account upgraded to Premium successfully")
                .data(accountService.toUserResponse(upgraded))
                .build());
    }

    @PostMapping("/{id}/downgrade")
    @Operation(summary = "Downgrade to Free (Admin)", description = "Admin: downgrade a user account to Free")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> downgradeToFree(@PathVariable UUID id) {
        log.info("Admin downgrading account {} to FREE", id);
        Account downgraded = accountService.downgradeToFree(id);
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("Account downgraded to Free")
                .data(accountService.toUserResponse(downgraded))
                .build());
    }

    @PostMapping("/{id}/upgrade")
    @Operation(summary = "Upgrade to Premium (Admin)", description = "Admin: upgrade a user account to Premium for N days")
    public ResponseEntity<ApiResponseDto<AccountResponseDto>> upgradeAccountToPremium(
            @PathVariable UUID id,
            @Valid @RequestBody UpgradeSubscriptionDto dto) {
        log.info("Admin upgrading account {} to PREMIUM for {} days", id, dto.getDurationDays());
        Account upgraded = accountService.upgradeToPremium(id, dto.getDurationDays());
        return ResponseEntity.ok(ApiResponseDto.<AccountResponseDto>builder()
                .status("success")
                .message("Account upgraded to Premium successfully")
                .data(accountService.toUserResponse(upgraded))
                .build());
    }
}


