package com.iems.projectservice.controller;

import com.iems.projectservice.annotation.RequireProjectPermission;
import com.iems.projectservice.dto.request.CreateCommentDto;
import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.dto.response.CommentResponseDto;
import com.iems.projectservice.entity.Comment;
import com.iems.projectservice.entity.enums.ProjectPermission;
import com.iems.projectservice.service.CommentService;
import com.iems.projectservice.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/issues/{issueId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comment")
public class CommentController {

    private final CommentService commentService;
    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Add comment to issue")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<Comment>> addComment(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @Valid @RequestBody CreateCommentDto dto) {
        UUID userId = projectService.getUserIdFromRequest();
        UUID parentId = dto.getParentCommentId() != null ? UUID.fromString(dto.getParentCommentId()) : null;
        Comment comment = commentService.addComment(issueId, userId, dto.getContent(), parentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Comment added successfully", comment));
    }

    @PatchMapping("/{commentId}")
    @Operation(summary = "Update comment")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<Comment>> updateComment(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID commentId,
            @RequestBody Map<String, String> body) {
        UUID userId = projectService.getUserIdFromRequest();
        Comment comment = commentService.updateComment(commentId, body.get("content"), userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment updated successfully", comment));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete comment")
    @RequireProjectPermission(ProjectPermission.ISSUE_UPDATE)
    public ResponseEntity<ApiResponseDto<Void>> deleteComment(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID commentId) {
        UUID userId = projectService.getUserIdFromRequest();
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Comment deleted successfully", null));
    }

    @GetMapping
    @Operation(summary = "Get issue comments")
    @RequireProjectPermission(ProjectPermission.ISSUE_READ)
    public ResponseEntity<ApiResponseDto<List<CommentResponseDto>>> getComments(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<CommentResponseDto> comments = commentService.getCommentsByIssue(issueId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Comments retrieved successfully", comments));
    }
}
