package com.iems.userservice.controller;

import com.iems.userservice.dto.request.UserRequestDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import com.iems.userservice.dto.response.UserResponseDto;
import com.iems.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "*")
public class UserController {
    @Autowired
    private UserService service;

    @Operation(summary = "Create user", description = "Create a new user in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User saved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to save user")
    })
    @PostMapping
    public ResponseEntity<ApiResponseDto<UserResponseDto>> saveUser(@RequestBody UserRequestDto userRequest) {
        try {
            UserResponseDto savedUser = service.saveUser(userRequest);
            return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "User saved successfully", savedUser));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to save user: " + e.getMessage(), null));
        }
    }

    @Operation(summary = "Get all users", description = "Retrieve a list of all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch users")
    })
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

    @Operation(summary = "Get user by ID", description = "Retrieve user details by unique ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch user")
    })
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to delete user")
    })
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

}