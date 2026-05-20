package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.BatchIssueTypeSyncRequest;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.IssueType;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.repository.ProjectRepository;
import com.iems.projectservice.service.IssueService;
import com.iems.projectservice.service.SubscriptionLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/issue-types")
@RequiredArgsConstructor
@Tag(name = "Issue Type")
public class IssueTypeController {

    private final IssueService issueService;
    private final ProjectRepository projectRepository;
    private final SubscriptionLimitService subscriptionLimitService;

    private String ownerSub(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(p -> p.getOwnerSubscription()).orElse("FREE");
    }

    @GetMapping
    @Operation(summary = "Get issue types")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueType>>> getIssueTypes(@PathVariable UUID projectId) {
        List<IssueType> types = issueService.getIssueTypes(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue types retrieved", types));
    }

    @PostMapping
    @Operation(summary = "Create issue type")
    @RequireProjectPermission(ProjectPermission.PROJECT_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueType>> createIssueType(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body) {
        subscriptionLimitService.checkCanModifyIssueType(ownerSub(projectId));
        IssueType type = issueService.createIssueType(projectId,
                body.get("name"), body.get("description"), body.get("iconUrl"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Issue type created", type));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync issue types in one request")
    @RequireProjectPermission(ProjectPermission.PROJECT_UPDATE)
    public ResponseEntity<ApiResponseDto<List<IssueType>>> syncIssueTypes(
            @PathVariable UUID projectId,
            @RequestBody BatchIssueTypeSyncRequest request) {
        subscriptionLimitService.checkCanModifyIssueType(ownerSub(projectId));
        List<IssueType> types = issueService.syncIssueTypes(projectId, request.getIssueTypes());
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue types synced", types));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update issue type")
    @RequireProjectPermission(ProjectPermission.PROJECT_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueType>> updateIssueType(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        subscriptionLimitService.checkCanModifyIssueType(ownerSub(projectId));
        IssueType type = issueService.updateIssueType(id,
                body.get("name"), body.get("description"), body.get("iconUrl"));
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue type updated", type));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete issue type")
    @RequireProjectPermission(ProjectPermission.PROJECT_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> deleteIssueType(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        subscriptionLimitService.checkCanModifyIssueType(ownerSub(projectId));
        issueService.deleteIssueType(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue type deleted", null));
    }
}
