package com.iems.userservice.controller;

import com.iems.userservice.dto.request.CreateRoleDto;
import com.iems.userservice.dto.request.UpdateRoleDto;
import com.iems.userservice.dto.response.ApiResponseDto;
import com.iems.userservice.dto.response.RoleDto;
import com.iems.userservice.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
public class RoleController {
    @Autowired
    private RoleService service;

    @Operation(summary = "Create role")
    @PostMapping
    public ResponseEntity<ApiResponseDto<RoleDto>> createRole(@RequestBody CreateRoleDto dto) {
        RoleDto created = service.createRole(dto);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Role created", created));
    }

    @Operation(summary = "Update role")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RoleDto>> updateRole(@PathVariable UUID id, @RequestBody UpdateRoleDto dto) {
        RoleDto updated = service.updateRole(id, dto);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Role updated", updated));
    }

    @Operation(summary = "Delete role")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> deleteRole(@PathVariable UUID id) {
        service.deleteRole(id);
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Role deleted", null));
    }

    @Operation(summary = "Get all roles")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<RoleDto>>> getAllRoles() {
        List<RoleDto> roles = service.getAllRoles();
        return ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Roles retrieved", roles));
    }

    @Operation(summary = "Get role by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<RoleDto>> getRoleById(@PathVariable UUID id) {
        return service.getRoleById(id)
                .map(r -> ResponseEntity.ok(new ApiResponseDto<>(HttpStatus.OK.value(), "Role found", r)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDto<>(HttpStatus.NOT_FOUND.value(), "Role not found", null)));
    }
}
