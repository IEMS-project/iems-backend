package com.iems.userservice.controller;

import com.iems.userservice.dto.request.CreateUserDto;
import com.iems.userservice.dto.request.UpdateUserDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import com.iems.userservice.dto.response.UserBasicInfoDto;
import com.iems.userservice.dto.response.UserResponseDto;
import com.iems.userservice.security.JwtUserDetails;
import com.iems.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService service;

    @Operation(summary = "Create user", description = "Create a new user in the system")
    @PostMapping
    public ResponseEntity<ApiResponseDto<UserResponseDto>> saveUser(@RequestBody CreateUserDto userRequest) {
        try {
            UserResponseDto savedUser = service.createUser(userRequest);
            return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "User saved successfully", savedUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to save user: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Get all users", description = "Retrieve a list of all users")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<UserResponseDto>>> getAllUsers() {
        try {
            List<UserResponseDto> users = service.getAllUsers();
            return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Users retrieved successfully", users));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to fetch users: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Get all users basic info", description = "Retrieve a list of all users")
    @GetMapping("/basic-infos")
    public ResponseEntity<ApiResponseDto<List<UserBasicInfoDto>>> getAllUserBasicInfos() {
        try {
            List<UserBasicInfoDto> users = service.getAllUserBasicInfos();
            return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Users retrieved successfully", users));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to fetch users: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Get user by ID", description = "Retrieve user details by unique ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> getUserById(@PathVariable UUID id) {
        try {
            return service.getUserById(id)
                    .map(userResponse -> ResponseEntity.ok(
                            new ApiResponseDto<>(HttpStatus.OK.value(), "User found", userResponse)
                    ))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "User not found", null))
                    );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to fetch user: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Delete user", description = "Delete a user by unique ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteUser(@PathVariable UUID id) {
        try {
            service.deleteUser(id);
            return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "User deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to delete user: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Update user by ID", description = "Update a user's information by ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserDto userRequest
    ) {
        try {
            return service.updateUser(id, userRequest)
                    .map(updated -> ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "User updated successfully", updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "User not found", null)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to update user: " + e.getMessage(), null));
        }
    }



    @Operation(summary = "Update my profile", description = "Update the profile of the authenticated user")
    @PutMapping("/me")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateMyProfile(
            @RequestBody CreateUserDto userRequest
    ) {
        try {
            // Lấy userId từ SecurityContext (được set trong JwtAuthenticationFilter)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            UUID userId = userDetails.getUserId();

            return service.updateMyProfile(userId, userRequest)
                    .map(updated -> ResponseEntity.ok(
                            new ApiResponseDto<>(HttpStatus.OK.value(), "Profile updated successfully", updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "User not found", null)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            "Failed to update profile: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Get my profile", description = "Retrieve the profile of the authenticated user")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> getMyProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
            UUID userId = userDetails.getUserId();

            return service.getUserById(userId)
                    .map(user -> ResponseEntity.ok(
                            new ApiResponseDto<>(HttpStatus.OK.value(), "Profile retrieved successfully", user)
                    ))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "User not found", null)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            "Failed to fetch profile: " + e.getMessage(), null));
        }
    }


}