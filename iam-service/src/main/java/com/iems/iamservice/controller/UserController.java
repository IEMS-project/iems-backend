package com.iems.iamservice.controller;

import com.iems.iamservice.dto.request.CreateUserDto;
import com.iems.iamservice.dto.request.UpdateAvatarDto;
import com.iems.iamservice.dto.request.UpdateUserDto;
import com.iems.iamservice.dto.request.UserIdsDto;
import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.response.AccountSubscriptionResponseDto;
import com.iems.iamservice.dto.response.UserBasicInfoDto;
import com.iems.iamservice.dto.response.UserResponseDto;
import com.iems.iamservice.security.JwtUserDetails;
import com.iems.iamservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

        private UUID getCurrentAccountId() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails userDetails)) {
                        throw new IllegalStateException("Unauthorized");
                }
                return userDetails.getUserId();
        }

        @Operation(summary = "Update avatar URL", description = "Update only the image field of current user")
        @PutMapping("/me/avatar")
        public ResponseEntity<ApiResponseDto<UserResponseDto>> updateMyAvatar(@RequestBody UpdateAvatarDto payload) {
                UUID accountId = getCurrentAccountId();

                return service.updateAvatarByAccountId(accountId, payload.getImageUrl())
                                .map(updated -> ResponseEntity.ok(ApiResponseDto.<UserResponseDto>builder()
                                                .status("success")
                                                .message("Avatar updated")
                                                .data(updated)
                                                .build()))
                                .orElseGet(() -> ResponseEntity.notFound().build());
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

        @Operation(summary = "Search users basic info", description = "Search users by name/email with pagination and optional exclusion list")
        @GetMapping("/basic-infos/search")
        public ResponseEntity<ApiResponseDto<Page<UserBasicInfoDto>>> searchUserBasicInfos(
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(required = false) List<UUID> excludeAccountIds) {
                Page<UserBasicInfoDto> users = service.searchUserBasicInfos(q, page, size, excludeAccountIds);
                return ResponseEntity.ok(ApiResponseDto.<Page<UserBasicInfoDto>>builder()
                                .status("success")
                                .message("Users searched successfully")
                                .data(users)
                                .build());
        }

        @Operation(summary = "Get project manager candidates", description = "Retrieve users who can be project managers")
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

        @Operation(summary = "Get user by Account ID", description = "Retrieve user details by account ID")
        @GetMapping("/by-account/{accountId}")
        public ResponseEntity<ApiResponseDto<UserResponseDto>> getUserByAccountId(@PathVariable UUID accountId) {
                log.info("Getting user with Account ID: {}", accountId);
                try {
                        return service.getUserByAccountId(accountId)
                                        .map(userResponse -> ResponseEntity.ok(
                                                        ApiResponseDto.<UserResponseDto>builder()
                                                                        .status("success")
                                                                        .message("User found")
                                                                        .data(userResponse)
                                                                        .build()))
                                        .orElseGet(() -> ResponseEntity.ok(
                                                        ApiResponseDto.<UserResponseDto>builder()
                                                                        .status("error")
                                                                        .message("User not found")
                                                                        .data(null)
                                                                        .build()));
                } catch (Exception e) {
                        log.warn("Error fetching user by accountId {}: {}", accountId, e.getMessage());
                        return ResponseEntity.ok(
                                        ApiResponseDto.<UserResponseDto>builder()
                                                        .status("error")
                                                        .message("User not found")
                                                        .data(null)
                                                        .build());
                }
        }

        @Operation(summary = "Get account subscription by Account ID", description = "Retrieve account subscription status by account ID")
        @GetMapping("/by-account/{accountId}/subscription")
        public ResponseEntity<ApiResponseDto<AccountSubscriptionResponseDto>> getAccountSubscription(
                        @PathVariable UUID accountId) {
                AccountSubscriptionResponseDto subscription = service.getAccountSubscription(accountId);
                return ResponseEntity.ok(ApiResponseDto.<AccountSubscriptionResponseDto>builder()
                                .status("success")
                                .message("Account subscription retrieved successfully")
                                .data(subscription)
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
                                                                .build()))
                                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @Operation(summary = "Update user by ID", description = "Update a user's information by ID")
        @PutMapping("/{id}")
        public ResponseEntity<ApiResponseDto<UserResponseDto>> updateUser(
                        @PathVariable UUID id,
                        @RequestBody UpdateUserDto userRequest) {
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
                        @RequestBody CreateUserDto userRequest) {
                UUID accountId = getCurrentAccountId();
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
                UUID accountId = getCurrentAccountId();
                log.info("Getting profile for account ID: {}", accountId);

                return service.getUserByAccountId(accountId)
                                .map(user -> ResponseEntity.ok(
                                                ApiResponseDto.<UserResponseDto>builder()
                                                                .status("success")
                                                                .message("Profile retrieved successfully")
                                                                .data(user)
                                                                .build()))
                                .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @Operation(summary = "Get users by IDs", description = "Retrieve multiple users by their IDs")
        @PostMapping("/by-ids")
        public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> getUsersByID(
                        @RequestBody UserIdsDto request) {
                log.info("Getting users by IDs: {}", request.getIds().size());
                List<UserResponseDto> users = service.getUsersByID(request);
                return ResponseEntity.ok(ApiResponseDto.<List<UserResponseDto>>builder()
                                .status("success")
                                .message("Users retrieved successfully")
                                .data(users)
                                .build());
        }

        @Operation(summary = "Get users by Account IDs", description = "Retrieve multiple users by their Account IDs")
        @PostMapping("/by-account-ids")
        public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> getUsersByAccountIds(
                        @RequestBody com.iems.iamservice.dto.request.AccountIdsDto request) {
                log.info("Getting users by Account IDs: {}", request.getAccountIds().size());
                List<UserResponseDto> users = service.getUsersByAccountIds(request);
                return ResponseEntity.ok(ApiResponseDto.<List<UserResponseDto>>builder()
                                .status("success")
                                .message("Users retrieved successfully")
                                .data(users)
                                .build());
        }

        @Operation(summary = "Get my notification preferences")
        @GetMapping("/me/notification-preferences")
        public ResponseEntity<ApiResponseDto<java.util.Map<String, Object>>> getMyNotificationPreferences() {
                UUID accountId = getCurrentAccountId();
                String prefsJson = service.getNotificationPreferences(accountId);
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                if (prefsJson != null && !prefsJson.isBlank()) {
                        try {
                                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                result = om.readValue(prefsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){});
                        } catch (Exception e) {
                                log.warn("Failed to parse notification preferences: {}", e.getMessage());
                        }
                }
                result.putIfAbsent("emailAssigned", true);
                result.putIfAbsent("emailMemberAdded", true);
                result.putIfAbsent("emailDueSoon", true);
                result.putIfAbsent("inAppToast", true);
                return ResponseEntity.ok(ApiResponseDto.<java.util.Map<String, Object>>builder()
                                .status("success").message("Preferences retrieved").data(result).build());
        }

        @Operation(summary = "Get notification preferences by account ID")
        @GetMapping("/by-account/{accountId}/notification-preferences")
        public ResponseEntity<ApiResponseDto<java.util.Map<String, Object>>> getNotificationPreferencesByAccountId(@PathVariable UUID accountId) {
                String prefsJson = service.getNotificationPreferences(accountId);
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                if (prefsJson != null && !prefsJson.isBlank()) {
                        try {
                                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                                result = om.readValue(prefsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>(){});
                        } catch (Exception e) {
                                log.warn("Failed to parse notification preferences: {}", e.getMessage());
                        }
                }
                result.putIfAbsent("emailAssigned", true);
                result.putIfAbsent("emailMemberAdded", true);
                result.putIfAbsent("emailDueSoon", true);
                result.putIfAbsent("inAppToast", true);
                return ResponseEntity.ok(ApiResponseDto.<java.util.Map<String, Object>>builder()
                                .status("success").message("Preferences retrieved").data(result).build());
        }

        @Operation(summary = "Update my notification preferences")
        @PatchMapping("/me/notification-preferences")
        public ResponseEntity<ApiResponseDto<Void>> updateMyNotificationPreferences(
                        @RequestBody java.util.Map<String, Object> preferences) {
                UUID accountId = getCurrentAccountId();
                try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        service.updateNotificationPreferences(accountId, om.writeValueAsString(preferences));
                        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                                        .status("success").message("Preferences updated").build());
                } catch (Exception e) {
                        log.error("Failed to update notification preferences: {}", e.getMessage());
                        return ResponseEntity.internalServerError().build();
                }
        }
}
