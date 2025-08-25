package com.iems.userservice.controller;

import com.iems.userservice.dto.request.UserRequestDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import com.iems.userservice.dto.response.UserResponseDto;
import com.iems.userservice.service.UserService;
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