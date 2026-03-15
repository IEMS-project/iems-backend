package com.iems.projectservice.controller;

import com.iems.projectservice.dto.request.ProjectMemberDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.ProjectMemberResponseDto;
import com.iems.projectservice.entity.ProjectMember;
import com.iems.projectservice.service.ProjectMemberService;
import com.iems.projectservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/members")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Project Member")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Add member to project")
    public ResponseEntity<ApiResponseDto<ProjectMember>> addMember(
            @PathVariable UUID projectId,
            @RequestBody ProjectMemberDto dto) {
        try {
            UUID currentUserId = projectService.getUserIdFromRequest();
            ProjectMember member = projectMemberService.addMemberToProject(
                    projectId, dto.getAccountId(), dto.getRoleId(), currentUserId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponseDto<>("success", "Member added successfully", member));
        } catch (Exception e) {
            log.error("Error adding member", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Remove member from project")
    public ResponseEntity<ApiResponseDto<Void>> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId) {
        try {
            projectMemberService.removeMember(projectId, accountId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Member removed successfully", null));
        } catch (Exception e) {
            log.error("Error removing member", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @PatchMapping("/{accountId}/role")
    @Operation(summary = "Change member role")
    public ResponseEntity<ApiResponseDto<ProjectMember>> updateMemberRole(
            @PathVariable UUID projectId,
            @PathVariable UUID accountId,
            @RequestParam UUID roleId) {
        try {
            ProjectMember member = projectMemberService.updateMemberRole(projectId, accountId, roleId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Member role updated successfully", member));
        } catch (Exception e) {
            log.error("Error updating member role", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }

    @GetMapping
    @Operation(summary = "Get project members")
    public ResponseEntity<ApiResponseDto<List<ProjectMemberResponseDto>>> getMembers(@PathVariable UUID projectId) {
        try {
            List<ProjectMemberResponseDto> members = projectMemberService.getProjectMembersEnriched(projectId);
            return ResponseEntity.ok(new ApiResponseDto<>("success", "Members retrieved successfully", members));
        } catch (Exception e) {
            log.error("Error getting members", e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDto<>("error", e.getMessage(), null));
        }
    }
}
