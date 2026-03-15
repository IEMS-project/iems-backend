package com.iems.projectservice.controller;

import com.iems.projectservice.dto.response.ApiResponseDto;
import com.iems.projectservice.entity.Attachment;
import com.iems.projectservice.service.AttachmentService;
import com.iems.projectservice.service.ProjectService;
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
@RequestMapping("/projects/{projectId}/issues/{issueId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachment")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Add attachment to issue")
    public ResponseEntity<ApiResponseDto<Attachment>> addAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestBody Map<String, Object> body) {
        UUID userId = projectService.getUserIdFromRequest();
        Attachment att = attachmentService.addAttachment(
                issueId,
                (String) body.get("fileId"),
                (String) body.get("fileName"),
                (String) body.get("fileUrl"),
                (String) body.get("fileType"),
                body.get("fileSize") != null ? Long.parseLong(body.get("fileSize").toString()) : null,
                userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseDto<>("success", "Attachment added successfully", att));
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Delete attachment")
    public ResponseEntity<ApiResponseDto<Void>> deleteAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Attachment deleted successfully", null));
    }

    @GetMapping
    @Operation(summary = "Get issue attachments")
    public ResponseEntity<ApiResponseDto<List<Attachment>>> getAttachments(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId) {
        List<Attachment> attachments = attachmentService.getAttachmentsByIssue(issueId);
        return ResponseEntity.ok(new ApiResponseDto<>("success", "Attachments retrieved successfully", attachments));
    }
}
