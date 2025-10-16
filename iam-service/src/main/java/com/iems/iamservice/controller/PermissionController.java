package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.service.PermissionService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Permission Management", description = "APIs for managing permissions")
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * Create new permission
     */
    @PostMapping
    @Operation(summary = "Create permission", description = "Create new permission")
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> create(@Valid @RequestBody CreatePermissionDto dto) {
        log.info("Creating permission: {}", dto.getCode());
        
        var created = permissionService.create(dto);
        var response = permissionService.toPermissionResponse(created);
        
        return ResponseEntity.created(URI.create("/api/permissions/" + created.getId()))
                .body(ApiResponseDto.<PermissionResponseDto>builder()
                        .status("success")
                        .message("Permission created successfully")
                        .data(response)
                        .build());
    }

    /**
     * Get list of permissions
     */
    @GetMapping
    @Operation(summary = "List permissions", description = "Get list of all permissions")
    public ResponseEntity<ApiResponseDto<List<PermissionResponseDto>>> list() {
        log.info("Getting all permissions");
        
        var data = permissionService.findAll().stream()
                .map(permissionService::toPermissionResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponseDto.<List<PermissionResponseDto>>builder()
                .status("success")
                .message("Permissions list retrieved successfully")
                .data(data)
                .build());
    }

    /**
     * Get permission information by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Permission details", description = "Get detailed permission information by ID")
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> get(@PathVariable UUID id) {
        log.info("Getting permission with ID: {}", id);
        
        var permission = permissionService.findById(id);
        var response = permissionService.toPermissionResponse(permission);
        
        return ResponseEntity.ok(ApiResponseDto.<PermissionResponseDto>builder()
                .status("success")
                .message("Permission information retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Update permission
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update permission", description = "Update permission information")
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> update(@PathVariable UUID id, @Valid @RequestBody UpdatePermissionDto dto) {
        log.info("Updating permission with ID: {}", id);
        
        var updated = permissionService.update(id, dto);
        var response = permissionService.toPermissionResponse(updated);
        
        return ResponseEntity.ok(ApiResponseDto.<PermissionResponseDto>builder()
                .status("success")
                .message("Permission updated successfully")
                .data(response)
                .build());
    }

    /**
     * Delete permission
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission", description = "Delete permission")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID id) {
        log.info("Deleting permission with ID: {}", id);
        
        permissionService.delete(id);
        
        return ResponseEntity.ok(ApiResponseDto.<Void>builder()
                .status("success")
                .message("Permission deleted successfully")
                .build());
    }
}


