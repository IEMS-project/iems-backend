package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateIssueDto;
import com.iems.projectservice.dto.request.UpdateIssueDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.IssueImportResultDto;
import com.iems.projectservice.dto.response.IssueResponseDto;
import com.iems.projectservice.dto.response.PagedResponseDto;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.exception.AppException;
import com.iems.projectservice.service.IssueService;
import com.iems.projectservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/issues")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Issue")
public class IssueController {

    private final IssueService issueService;
    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create issue")
    @RequireProjectPermission(ProjectPermission.ISSUE_CREATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> createIssue(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateIssueDto dto) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        IssueResponseDto issue = issueService.createIssue(projectId, dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Issue created successfully", issue));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import issues from Excel")
    @RequireProjectPermission(ProjectPermission.ISSUE_CREATE)
    public ResponseEntity<ApiResponseDto<IssueImportResultDto>> importIssues(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        IssueImportResultDto result = issueService.importIssuesFromExcel(projectId, file, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issues imported successfully", result));
    }

    @GetMapping("/import-template")
    @Operation(summary = "Download issue import template")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<byte[]> downloadImportTemplate(@PathVariable UUID projectId) {
        byte[] content = issueService.generateImportTemplate(projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=issue-import-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/export")
    @Operation(summary = "Export issues to Excel")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<byte[]> exportIssues(@PathVariable UUID projectId) {
        byte[] content = issueService.exportIssuesToExcel(projectId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=issues-export.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PatchMapping("/{issueId}")
    @Operation(summary = "Update issue")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> updateIssue(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @Valid @RequestBody UpdateIssueDto dto) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        IssueResponseDto issue = issueService.updateIssue(issueId, dto, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue updated successfully", issue));
    }

    @DeleteMapping("/{issueId}")
    @Operation(summary = "Delete issue")
    @RequireProjectPermission(ProjectPermission.ISSUE_DELETE)
    public ResponseEntity<ApiResponseDto<Void>> deleteIssue(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        issueService.deleteIssue(issueId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue deleted successfully", null));
    }

    @GetMapping("/{issueId}")
    @Operation(summary = "Get issue by ID")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> getIssue(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) throws AppException {
        IssueResponseDto issue = issueService.getIssueById(issueId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue retrieved successfully", issue));
    }

    @GetMapping
    @Operation(summary = "Get all issues in project")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getIssues(@PathVariable UUID projectId) throws AppException {
        List<IssueResponseDto> issues = issueService.getIssuesByProject(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issues retrieved successfully", issues));
    }

    @GetMapping("/paged")
    @Operation(summary = "Get issues in project with pagination")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<PagedResponseDto<IssueResponseDto>>> getIssuesPaged(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) throws AppException {
        PagedResponseDto<IssueResponseDto> issues = issueService.getIssuesByProjectPaged(projectId, page, size);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issues retrieved successfully", issues));
    }

    @GetMapping("/backlog")
    @Operation(summary = "Get product backlog (issues not in any sprint)")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getBacklog(@PathVariable UUID projectId) throws AppException {
        List<IssueResponseDto> issues = issueService.getBacklog(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Backlog retrieved successfully", issues));
    }

    @GetMapping("/{issueId}/children")
    @Operation(summary = "Get child issues (hierarchy)")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getChildIssues(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) throws AppException {
        List<IssueResponseDto> children = issueService.getChildIssues(issueId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Child issues retrieved successfully", children));
    }

    @PatchMapping("/{issueId}/assign")
    @Operation(summary = "Assign issue to user")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> assignIssue(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestParam UUID assigneeId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        UpdateIssueDto dto = new UpdateIssueDto();
        dto.setAssigneeId(assigneeId);
        IssueResponseDto issue = issueService.updateIssue(issueId, dto, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue assigned successfully", issue));
    }

    @PatchMapping("/{issueId}/status")
    @Operation(summary = "Change issue status")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> changeStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestParam UUID statusId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        UpdateIssueDto dto = new UpdateIssueDto();
        dto.setStatusId(statusId);
        IssueResponseDto issue = issueService.updateIssue(issueId, dto, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue status updated successfully", issue));
    }

    @PatchMapping("/{issueId}/sprint")
    @Operation(summary = "Move issue to sprint")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> moveToSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestParam UUID sprintId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        IssueResponseDto issue = issueService.addToSprint(issueId, sprintId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue moved to sprint successfully", issue));
    }

    @DeleteMapping("/{issueId}/sprint")
    @Operation(summary = "Remove issue from sprint")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<IssueResponseDto>> removeFromSprint(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        IssueResponseDto issue = issueService.removeFromSprint(issueId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Issue removed from sprint successfully", issue));
    }

    @GetMapping("/my-issues")
    @Operation(summary = "Get my assigned issues")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<IssueResponseDto>>> getMyIssues(@PathVariable UUID projectId) throws AppException {
        UUID userId = projectService.getUserIdFromRequest();
        List<IssueResponseDto> issues = issueService.getMyIssues(userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "My issues retrieved successfully", issues));
    }
}
