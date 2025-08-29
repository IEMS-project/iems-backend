package com.iems.iamservice.controller;

import com.iems.iamservice.dto.ApiResponseDto;
import com.iems.iamservice.dto.request.CreateRoleDto;
import com.iems.iamservice.dto.request.UpdateRoleDto;
import com.iems.iamservice.dto.response.RoleResponseDto;
import com.iems.iamservice.mapper.IamMapper;
import com.iems.iamservice.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/iam/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "APIs for managing roles")
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @Operation(summary = "Create role", description = "Create a new role")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> create(@RequestBody CreateRoleDto dto) {
        var created = roleService.create(dto);
        var body = new ApiResponseDto<>("success", "created", IamMapper.toRoleResponse(created));
        return ResponseEntity.created(URI.create("/api/iam/roles/" + created.getId())).body(body);
    }

    @GetMapping
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles retrieved successfully")
    })
    @Operation(summary = "List roles", description = "Get all roles")
    public ResponseEntity<ApiResponseDto<List<RoleResponseDto>>> list() {
        var data = roleService.findAll().stream().map(IamMapper::toRoleResponse).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", data));
    }

    @GetMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @Operation(summary = "Get role", description = "Get a role by its ID")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> get(@PathVariable Long id) {
        var role = roleService.findById(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "ok", IamMapper.toRoleResponse(role)));
    }

    @PatchMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @Operation(summary = "Update role", description = "Update details of an existing role")
    public ResponseEntity<ApiResponseDto<RoleResponseDto>> update(@PathVariable Long id, @RequestBody UpdateRoleDto dto) {
        var updated = roleService.update(id, dto);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "updated", IamMapper.toRoleResponse(updated)));
    }

    @DeleteMapping("/{id}")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role deleted"),
            @ApiResponse(responseCode = "404", description = "Role not found")
    })
    @Operation(summary = "Delete role", description = "Delete a role by its ID")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "deleted", null));
    }
}


