package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.ProjectMemberDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project Member")
public class ProjectMemberController {
    
    private final ProjectMemberService projectMemberService;
    
    @PostMapping
    @Operation(summary = "Add member to project", description = "Add a new member to the project with specified role")
    @PreAuthorize("hasAuthority('PROJECT_MANAGE')")
    public ResponseEntity<ApiResponseDto<ProjectMemberResponseDto>> addMember(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectMemberDto memberDto) {
        try {
            ProjectMemberResponseDto member = projectMemberService.addMemberToProject(
                    projectId, memberDto.getUserId(), memberDto.getRoleId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Member added to project successfully", member));
        } catch (Exception e) {
            log.error("Error adding member to project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping
    @Operation(summary = "Get project members", description = "Get all members of the project")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ResponseEntity<ApiResponseDto<List<ProjectMemberResponseDto>>> getProjectMembers(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId) {
        try {
            List<ProjectMemberResponseDto> members = projectMemberService.getProjectMembers(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Project members retrieved successfully", members));
        } catch (Exception e) {
            log.error("Error getting project members", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @GetMapping("/role/{roleId}")
    @Operation(summary = "Get members by role", description = "Get project members filtered by specific role")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public ResponseEntity<ApiResponseDto<List<ProjectMemberResponseDto>>> getMembersByRole(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "Role ID to filter by", required = true)
            @PathVariable UUID roleId) {
        try {
            List<ProjectMemberResponseDto> members = projectMemberService.getMembersByRole(projectId, roleId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Members by role retrieved successfully", members));
        } catch (Exception e) {
            log.error("Error getting project members by role", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @PutMapping("/{userId}/role")
    @Operation(summary = "Update member role", description = "Update the role of a project member")
    @PreAuthorize("hasAuthority('PROJECT_MANAGE')")
    public ResponseEntity<ApiResponseDto<ProjectMemberResponseDto>> updateMemberRole(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "User ID to update role for", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "New role ID for the member", required = true)
            @RequestParam UUID newRoleId) {
        try {
            ProjectMemberResponseDto member = projectMemberService.updateMemberRole(
                    projectId, userId, newRoleId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Member role updated successfully", member));
        } catch (Exception e) {
            log.error("Error updating member role", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
    
    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove member from project", description = "Remove a member from the project")
    @PreAuthorize("hasAuthority('PROJECT_MANAGE')")
    public ResponseEntity<ApiResponseDto<Void>> removeMember(
            @Parameter(description = "Project ID", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "User ID to remove from project", required = true)
            @PathVariable UUID userId) {
        try {
            projectMemberService.removeMemberFromProject(projectId, userId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Member removed from project successfully", null));
        } catch (Exception e) {
            log.error("Error removing member from project", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
