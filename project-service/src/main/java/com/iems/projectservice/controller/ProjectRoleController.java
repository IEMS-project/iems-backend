package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectAllowedRoleDto;
import com.iems.projectservice.entity.ProjectAllowedRole;
import com.iems.projectservice.service.ProjectAllowedRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/roles")
@RequiredArgsConstructor
@Tag(name = "Project Roles")
public class ProjectRoleController {

    private final ProjectAllowedRoleService service;

    @GetMapping
    @Operation(summary = "List project roles")
    public ResponseEntity<ApiResponseDto<List<ProjectAllowedRoleDto>>> list(@PathVariable UUID projectId) {
        List<ProjectAllowedRole> list = service.list(projectId);
        List<ProjectAllowedRoleDto> dto = list.stream()
                .map(r -> new ProjectAllowedRoleDto(r.getId(), r.getRoleId(), r.getRoleName()))
                .toList();
        return ResponseEntity.ok(new ApiResponseDto<>("success", "OK", dto));
    }

    @PostMapping
    @Operation(summary = "Add role to project")
    public ResponseEntity<ApiResponseDto<ProjectAllowedRoleDto>> add(@PathVariable UUID projectId,
                                                                  @RequestBody Map<String, String> payload) {
        UUID roleId = UUID.fromString(payload.get("roleId"));
        String roleName = payload.getOrDefault("roleName", "");
        ProjectAllowedRole saved = service.add(projectId, roleId, roleName);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Created",
                new ProjectAllowedRoleDto(saved.getId(), saved.getRoleId(), saved.getRoleName())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role from project")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        service.delete(projectId, id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Deleted", null));
    }
}


