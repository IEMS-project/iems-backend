package com.iems.iamservice.controller;

import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateAvatarDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.request.UserIdsDto;
import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.response.UserBasicInfoDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.security.JwtUserDetails;
import com.iems.iamservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService service;

    @Operation(summary = "Update avatar URL", description = "Update only the image field of current user")
    @PutMapping("/me/avatar")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateMyAvatar(@RequestBody UpdateAvatarDto payload) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            UUID userId = userDetails.getUserId();

            return service.updateAvatar(userId, payload.getImageUrl())
                    .map(updated -> ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                            .status("success")
                            .message("Avatar updated")
                            .data(updated)
                            .build()))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to update avatar", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponseDto.<UserResponseDto>builder()
                            .status("error")
                            .message("Failed to update avatar: " + e.getMessage())
                            .build());
        }
    }

    @Operation(summary = "Create user", description = "Create a new user in the system")
    @PostMapping
    public ResponseEntity<ApiResponseDto<UserResponseDto>> saveUser(@RequestBody CreateUserDto userRequest) {
        log.info("Creating user: {}", userRequest.getEmail());
        UserResponseDto savedUser = service.createUser(userRequest);
        return ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                .status("success")
                .message("User saved successfully")
                .data(savedUser)
                .build());
    }

    @Operation(summary = "Get all users", description = "Retrieve a list of all users")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> getAllUsers() {
        log.info("Getting all users");
        List<UserResponseDto> users = service.getAllUsers();
        return ResponseEntity.ok(ApiResponseDto.<List<UserResponseDto>>builder()
                .status("success")
                .message("Users retrieved successfully")
                .data(users)
                .build());
    }

    @Operation(summary = "Get all users basic info", description = "Retrieve a list of all users")
    @GetMapping("/basic-infos")
    public ResponseEntity<ApiResponseDto<List<UserBasicInfoDto>>> getAllUserBasicInfos() {
        log.info("Getting all user basic infos");
        List<UserBasicInfoDto> users = service.getAllUserBasicInfos();
        return ResponseEntity.ok(ApiResponseDto.<List<UserBasicInfoDto>>builder()
                .status("success")
                .message("Users retrieved successfully")
                .data(users)
                .build());
    }

    @Operation(summary = "Get project manager candidates", description = "Retrieve users who can be project managers (ADMIN or PROJECT_MANAGER roles)")
    @GetMapping("/project-manager-candidates")
    public ResponseEntity<ApiResponseDto<List<UserBasicInfoDto>>> getProjectManagerCandidates() {
        log.info("Getting project manager candidates");
        List<UserBasicInfoDto> users = service.getProjectManagerCandidates();
        return ResponseEntity.ok(ApiResponseDto.<List<UserBasicInfoDto>>builder()
                .status("success")
                .message("Project manager candidates retrieved successfully")
                .data(users)
                .build());
    }

    @Operation(summary = "Get user by ID", description = "Retrieve user details by unique ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> getUserById(@PathVariable UUID id) {
        log.info("Getting user with ID: {}", id);
        return service.getUserById(id)
                .map(userResponse -> ResponseEntity.ok(
                        ApiResponseDto.<UserResponseDto>builder()
                                .status("success")
                                .message("User found")
                                .data(userResponse)
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete user", description = "Delete a user by unique ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteUser(@PathVariable UUID id) {
        log.info("Deleting user with ID: {}", id);
        service.deleteUser(id);
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("User deleted successfully")
                .build());
    }

    @Operation(summary = "Update user by ID", description = "Update a user's information by ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserDto userRequest
    ) {
        log.info("Updating user with ID: {}", id);
        return service.updateUser(id, userRequest)
                .map(updated -> ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                        .status("success")
                        .message("User updated successfully")
                        .data(updated)
                        .build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update my profile", description = "Update the profile of the authenticated user")
    @PutMapping("/me")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateMyProfile(
            @RequestBody CreateUserDto userRequest
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID accountId = userDetails.getUserId(); // getUserId() returns accountId
        log.info("Updating profile for account ID: {}", accountId);

        return service.updateMyProfile(accountId, userRequest)
                .map(updated -> ResponseEntity.ok(
                        ApiResponseDto.<UserResponseDto>builder()
                                .status("success")
                                .message("Profile updated successfully")
                                .data(updated)
                                .build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get my profile", description = "Retrieve the profile of the authenticated user")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        UUID accountId = userDetails.getUserId(); // getUserId() returns accountId
        log.info("Getting profile for account ID: {}", accountId);

        return service.getUserByAccountId(accountId)
                .map(user -> ResponseEntity.ok(
                        ApiResponseDto.<UserResponseDto>builder()
                                .status("success")
                                .message("Profile retrieved successfully")
                                .data(user)
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get users by IDs", description = "Retrieve multiple users by their IDs")
    @PostMapping("/by-ids")
    public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> getUsersByID(
            @RequestBody UserIdsDto request
    ) {
        log.info("Getting users by IDs: {}", request.getIds().size());
        List<UserResponseDto> users = service.getUsersByID(request);
        return ResponseEntity.ok(ApiResponseDto.<List<UserResponseDto>>builder()
                .status("success")
                .message("Users retrieved successfully")
                .data(users)
                .build());
    }
}
