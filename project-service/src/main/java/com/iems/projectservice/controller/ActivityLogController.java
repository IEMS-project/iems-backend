package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ActivityLogResponseDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/activities")
@RequiredArgsConstructor
@Tag(name = "Activity Log")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    @Operation(summary = "Get project activity log")
    public ResponseEntity<ApiResponseDto<List<ActivityLogResponseDto>>> getProjectActivities(
            @PathVariable UUID projectId) {
        List<ActivityLogResponseDto> activities = activityLogService.getProjectActivities(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Activities retrieved successfully", activities));
    }

    @GetMapping("/issues/{issueId}")
    @Operation(summary = "Get issue activity log")
    public ResponseEntity<ApiResponseDto<List<ActivityLogResponseDto>>> getIssueActivities(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<ActivityLogResponseDto> activities = activityLogService.getIssueActivities(issueId);
        return ResponseEntity
                .ok(new ApiResponseDto<>("success", "Issue activities retrieved successfully", activities));
    }
}
