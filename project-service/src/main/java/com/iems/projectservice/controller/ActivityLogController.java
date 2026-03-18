package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.response.ActivityLogResponseDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.PagedResponseDto;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/activities")
@RequiredArgsConstructor
@Tag(name = "Activity Log")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    @Operation(summary = "Get project activity log")
    @RequireProjectPermission(ProjectPermission.PROJECT_READ)
    public ResponseEntity<ApiResponseDto<PagedResponseDto<ActivityLogResponseDto>>> getProjectActivities(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponseDto<ActivityLogResponseDto> result = activityLogService.getProjectActivities(
                projectId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Activities retrieved successfully", result));
    }

    @GetMapping("/issues/{issueId}")
    @Operation(summary = "Get issue activity log")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<PagedResponseDto<ActivityLogResponseDto>>> getIssueActivities(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponseDto<ActivityLogResponseDto> result = activityLogService.getIssueActivities(
                issueId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue activities retrieved successfully", result));
    }
}
