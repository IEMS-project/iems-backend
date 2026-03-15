package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.IssueType;
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
@RequestMapping("/projects/{projectId}/issue-types")
@RequiredArgsConstructor
@Tag(name = "Issue Type")
public class IssueTypeController {

    private final IssueService issueService;

    @GetMapping
    @Operation(summary = "Get issue types")
    public ResponseEntity<ApiResponseDto<List<IssueType>>> getIssueTypes(@PathVariable UUID projectId) {
        List<IssueType> types = issueService.getIssueTypes(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue types retrieved", types));
    }

    @PostMapping
    @Operation(summary = "Create issue type")
    public ResponseEntity<ApiResponseDto<IssueType>> createIssueType(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body) {
        IssueType type = issueService.createIssueType(projectId,
                body.get("name"), body.get("description"), body.get("iconUrl"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Issue type created", type));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update issue type")
    public ResponseEntity<ApiResponseDto<IssueType>> updateIssueType(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        IssueType type = issueService.updateIssueType(id,
                body.get("name"), body.get("description"), body.get("iconUrl"));
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue type updated", type));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete issue type")
    public ResponseEntity<ApiResponseDto<Void>> deleteIssueType(
            @PathVariable UUID projectId,
            @PathVariable UUID id) {
        issueService.deleteIssueType(id);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue type deleted", null));
    }
}
