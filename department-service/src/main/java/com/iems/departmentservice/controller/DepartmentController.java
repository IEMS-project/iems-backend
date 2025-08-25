package com.iems.departmentservice.controller;

import com.iems.departmentservice.dto.ApiResponse;
import com.iems.departmentservice.dto.CreateDepartmentDto;
import com.iems.departmentservice.dto.DepartmentResponseDto;
import com.iems.departmentservice.service.DepartmentService;
import com.iems.departmentservice.dto.UpdateDepartmentDto;
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
@CrossOrigin(origins = "*")
public class DepartmentController {
    @Autowired
    private DepartmentService service;

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponseDto>> saveDepartment(
            @Valid @RequestBody CreateDepartmentDto createDto, @RequestHeader("X-User-Id") UUID userId) {
        try {
            DepartmentResponseDto responseDto = service.saveDepartment(createDto, userId);
            return ResponseEntity.ok(new ApiResponse<>("success", "Department created successfully", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", "Error: Missing required fields or invalid data", null));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponseDto>>> getAllDepartments() {
        try {
            List<DepartmentResponseDto> departments = service.getAllDepartments();
            return ResponseEntity.ok(new ApiResponse<>("success", "Departments retrieved successfully", departments));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", "Failed to retrieve departments", Collections.emptyList()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponseDto>> getDepartmentById(@PathVariable UUID id) {
        try {
            return service.getDepartmentById(id)
                    .map(dept -> ResponseEntity.ok(new ApiResponse<DepartmentResponseDto>("success", "Department retrieved successfully", dept)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<DepartmentResponseDto>("error", "Department not found", null)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", "Failed to retrieve department", null));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponseDto>> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentDto updateDto,
            @RequestHeader("X-User-Id") UUID userId) {
        try {
            return service.updateDepartment(id, updateDto, userId)
                    .map(updated -> ResponseEntity.ok(new ApiResponse<DepartmentResponseDto>("success", "Department updated successfully", updated)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiResponse<DepartmentResponseDto>("error", "Department not found", null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", "Failed to update department", null));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteDepartment(@PathVariable UUID id) {
        try {
            boolean deleted = service.deleteDepartment(id);
            if (deleted) {
                return ResponseEntity.ok(new ApiResponse<>("success", "Department deleted successfully", null));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>("error", "Department not found", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>("error", "Failed to delete department", null));
        }
    }


}