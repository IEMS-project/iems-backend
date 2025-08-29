package com.iems.departmentservice.controller;

import com.iems.departmentservice.dto.ApiResponseDto;
import com.iems.departmentservice.dto.CreateDepartmentDto;
import com.iems.departmentservice.dto.DepartmentResponseDto;
import com.iems.departmentservice.service.DepartmentService;
import com.iems.departmentservice.dto.UpdateDepartmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@CrossOrigin(origins = "*")
@Tag(name = "Department API", description = "Manage departments")
public class DepartmentController {
    @Autowired
    private DepartmentService service;

    @PostMapping
    @Operation(summary = "Create a new department", description = "Add a new department with provided details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Department created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing fields"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })

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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Departments get successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to get departments")
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Department not found"),
            @ApiResponse(responseCode = "500", description = "Failed to retrieve department")
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Department not found"),
            @ApiResponse(responseCode = "500", description = "Failed to update department")
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Department deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Department not found"),
            @ApiResponse(responseCode = "500", description = "Failed to delete department")
    })
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


}