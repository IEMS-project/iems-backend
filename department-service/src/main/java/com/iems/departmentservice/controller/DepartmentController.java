package com.iems.departmentservice.controller;

import com.iems.departmentservice.dto.request.AddUserToDepartmentDto;
import com.iems.departmentservice.dto.response.ApiResponseDto;
import com.iems.departmentservice.dto.request.CreateDepartmentDto;
import com.iems.departmentservice.dto.response.DepartmentResponseDto;
import com.iems.departmentservice.dto.response.DepartmentUserDto;
import com.iems.departmentservice.dto.response.DepartmentWithUsersDto;
import com.iems.departmentservice.service.DepartmentService;
import com.iems.departmentservice.dto.request.UpdateDepartmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
@Tag(name = "Department API", description = "Manage departments")
public class DepartmentController {
    @Autowired
    private DepartmentService service;

    @PostMapping
    @Operation(summary = "Create a new department", description = "Add a new department with provided details")
    public ResponseEntity<ApiResponseDto<DepartmentResponseDto>> saveDepartment(
            @Valid @RequestBody CreateDepartmentDto createDto, @RequestHeader("X-User-Id") UUID userId) {
        try {
            DepartmentResponseDto responseDto = service.saveDepartment(createDto, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Department created successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Error: Missing required fields or invalid data", null));
        }
    }

    @GetMapping
    @Operation(summary = "Get all departments", description = "Get a list of all departments")
    public ResponseEntity<ApiResponseDto<List<DepartmentResponseDto>>> getAllDepartments() {
        try {
            List<DepartmentResponseDto> departments = service.getAllDepartments();
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Departments retrieved successfully", departments));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to retrieve departments", Collections.emptyList()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Retrieve details of a department by its ID")
    public ResponseEntity<ApiResponseDto<DepartmentResponseDto>> getDepartmentById(@PathVariable UUID id) {
        try {
            return service.getDepartmentById(id)
                    .map(dept -> ResponseEntity.ok(new ApiResponseDto<DepartmentResponseDto>("success", "Department retrieved successfully", dept)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<DepartmentResponseDto>("error", "Department not found", null)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to retrieve department", null));
        }
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update department", description = "Update department details by ID")
    public ResponseEntity<ApiResponseDto<DepartmentResponseDto>> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentDto updateDto,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            return service.updateDepartment(id, updateDto, userId)
                    .map(updated -> ResponseEntity.ok(new ApiResponseDto<DepartmentResponseDto>("success", "Department updated successfully", updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<DepartmentResponseDto>("error", "Department not found", null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to update department", null));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete department", description = "Delete a department by ID")
    public ResponseEntity<ApiResponseDto<Object>> deleteDepartment(@PathVariable UUID id) {
        try {
            boolean deleted = service.deleteDepartment(id);
            if (deleted) {
                return ResponseEntity.ok(new ApiResponseDto<>("success", "Department deleted successfully", null));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDto<>("error", "Department not found", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to delete department", null));
        }
    }

    @PostMapping("/{departmentId}/users")
    @Operation(summary = "Add user to department", description = "Add a user to a specific department")
    public ResponseEntity<ApiResponseDto<DepartmentUserDto>> addUserToDepartment(
            @PathVariable UUID departmentId,
            @Valid @RequestBody AddUserToDepartmentDto addUserDto,
            @RequestHeader("X-User-Id") UUID currentUserId) {
        try {
            DepartmentUserDto responseDto = service.addUserToDepartment(departmentId, addUserDto, currentUserId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "User added to department successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Error: Missing required fields or invalid data", null));
        }
    }

    @DeleteMapping("/{departmentId}/users/{userId}")
    @Operation(summary = "Remove user from department", description = "Remove a user from a specific department")
    public ResponseEntity<ApiResponseDto<Object>> removeUserFromDepartment(
            @PathVariable UUID departmentId,
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") UUID currentUserId) {
        try {
            boolean removed = service.removeUserFromDepartment(departmentId, userId, currentUserId);
            if (removed) {
                return ResponseEntity.ok(new ApiResponseDto<>("success", "User removed from department successfully", null));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDto<>("error", "User not found in department", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to remove user from department", null));
        }
    }

    @GetMapping("/users/{userId}/departments")
    @Operation(summary = "Get departments of user", description = "Get list of departments that a user belongs to")
    public ResponseEntity<ApiResponseDto<List<DepartmentUserDto>>> getDepartmentsOfUser(@PathVariable UUID userId) {
        try {
            List<DepartmentUserDto> departments = service.getDepartmentsOfUser(userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "User departments retrieved successfully", departments));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to retrieve user departments", Collections.emptyList()));
        }
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "Get department with users", description = "Retrieve department details with enriched user information")
    public ResponseEntity<ApiResponseDto<DepartmentWithUsersDto>> getDepartmentWithUsers(@PathVariable UUID id) {
        try {
            return service.getDepartmentWithUsersById(id)
                    .map(dept -> ResponseEntity.ok(new ApiResponseDto<>("success", "Department with users retrieved successfully", dept)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponseDto<DepartmentWithUsersDto>("error", "Department not found", null)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponseDto<>("error", "Failed to retrieve department with users", null));
        }
    }
}