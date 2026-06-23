package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.service.IssueService;
import com.iems.projectservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/issues")
@RequiredArgsConstructor
@Tag(name = "Global Issue")
public class GlobalIssueController {

    private final IssueService issueService;
    private final ProjectService projectService;

    @GetMapping("/my-assigned")
    @Operation(summary = "Get all issues assigned to current user across all projects")
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getMyAssignedIssues() {
        UUID userId = projectService.getUserIdFromRequest();
        List<IssueResponseDto> issues = issueService.getMyIssues(userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Global assigned issues retrieved", issues));
    }
}
