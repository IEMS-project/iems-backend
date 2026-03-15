package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.IssuePriority;
import com.iems.projectservice.service.IssueService;
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
@RequestMapping("/projects/{projectId}/issue-priorities")
@RequiredArgsConstructor
@Tag(name = "Issue Priority")
public class IssuePriorityController {

    private final IssueService issueService;

    @GetMapping
    @Operation(summary = "Get issue priorities")
    public ResponseEntity<ApiResponseDto<List<IssuePriority>>> getIssuePriorities(@PathVariable UUID projectId) {
        List<IssuePriority> priorities = issueService.getIssuePriorities(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue priorities retrieved", priorities));
    }

    @PostMapping
    @Operation(summary = "Create issue priority")
    public ResponseEntity<ApiResponseDto<IssuePriority>> createIssuePriority(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body) {
        IssuePriority priority = issueService.createIssuePriority(projectId,
                body.get("name"), body.get("iconUrl"), body.get("color"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Issue priority created", priority));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update issue priority")
    public ResponseEntity<ApiResponseDto<IssuePriority>> updateIssuePriority(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        IssuePriority priority = issueService.updateIssuePriority(id,
                body.get("name"), body.get("iconUrl"), body.get("color"));
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue priority updated", priority));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete issue priority")
    public ResponseEntity<ApiResponseDto<Void>> deleteIssuePriority(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        issueService.deleteIssuePriority(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue priority deleted", null));
    }
}
