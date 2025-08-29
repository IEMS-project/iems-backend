package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreatePermissionDto;
import com.iems.iamservice.dto.request.UpdatePermissionDto;
import com.iems.iamservice.dto.response.PermissionResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "APIs for managing permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    @Operation(summary = "Create permission", description = "Create a new permission")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Permission created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> create(@RequestBody CreatePermissionDto dto) {
        var created = permissionService.create(dto);
        var body = new ApiResponseDto<>("success", "created", IamMapper.toPermissionResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/permissions/" + created.getId())).body(body);
    }

    @GetMapping
    @Operation(summary = "List permissions", description = "Retrieve all permissions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissions retrieved successfully")
    })
    public ResponseEntity<ApiResponseDto<List<PermissionResponseDto>>> list() {
        var data = permissionService.findAll().stream().map(IamMapper::toPermissionResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission", description = "Retrieve a permission by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Permission not found")
    })
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> get(@PathVariable Long id) {
        var p = permissionService.findById(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", IamMapper.toPermissionResponse(p)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update permission", description = "Update details of an existing permission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission updated"),
            @ApiResponse(responseCode = "404", description = "Permission not found")
    })
    public ResponseEntity<ApiResponseDto<PermissionResponseDto>> update(@PathVariable Long id, @RequestBody UpdatePermissionDto dto) {
        var updated = permissionService.update(id, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "updated", IamMapper.toPermissionResponse(updated)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission", description = "Delete a permission by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission deleted"),
            @ApiResponse(responseCode = "404", description = "Permission not found")
    })
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "deleted", null));
    }
}


