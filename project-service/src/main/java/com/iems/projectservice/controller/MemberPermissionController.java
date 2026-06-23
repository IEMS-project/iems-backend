package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.MemberPermissionsResponseDto;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.service.MemberPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/members/{accountId}/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Member Permissions")
public class MemberPermissionController {

    private final MemberPermissionService memberPermissionService;

    @GetMapping
    @Operation(summary = "Get direct permission overrides for a member")
    @RequireProjectPermission(ProjectPermission.ROLE_READ)
    public ResponseEntity<ApiResponseDto<MemberPermissionsResponseDto>> getMemberPermissions(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId) {
        try {
            MemberPermissionsResponseDto result = memberPermissionService.getMemberPermissions(projectId, accountId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Member permissions retrieved", result));
        } catch (Exception e) {
            log.error("Error getting member permissions", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{permCode}/grant")
    @Operation(summary = "Grant a permission directly to a member")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> grantPermission(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId,
            @PathVariable String permCode) {
        try {
            ProjectPermission permission = parsePermission(permCode);
            memberPermissionService.grantPermission(projectId, accountId, permission);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permission granted to member", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", "Invalid permission code: " + permCode, null));
        } catch (Exception e) {
            log.error("Error granting permission to member", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PostMapping("/{permCode}/deny")
    @Operation(summary = "Deny a permission directly for a member")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> denyPermission(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId,
            @PathVariable String permCode) {
        try {
            ProjectPermission permission = parsePermission(permCode);
            memberPermissionService.denyPermission(projectId, accountId, permission);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permission denied for member", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", "Invalid permission code: " + permCode, null));
        } catch (Exception e) {
            log.error("Error denying permission for member", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{permCode}")
    @Operation(summary = "Reset a direct permission override for a member")
    @RequireProjectPermission(ProjectPermission.ROLE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> resetPermission(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId,
            @PathVariable String permCode) {
        try {
            ProjectPermission permission = parsePermission(permCode);
            memberPermissionService.resetPermission(projectId, accountId, permission);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Permission override reset", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", "Invalid permission code: " + permCode, null));
        } catch (Exception e) {
            log.error("Error resetting permission override", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    private ProjectPermission parsePermission(String permCode) {
        return ProjectPermission.valueOf(permCode.toUpperCase());
    }
}
