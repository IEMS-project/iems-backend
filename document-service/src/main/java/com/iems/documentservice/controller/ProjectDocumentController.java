package com.iems.documentservice.controller;

import com.iems.documentservice.dto.response.ApiResponseDto;
import com.iems.documentservice.dto.response.ProjectDocumentResponse;
import com.iems.documentservice.service.ProjectDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
@RequiredArgsConstructor
@Tag(name = "Project Documents API", description = "Documents scoped to a specific project (members only)")
public class ProjectDocumentController {

    private final ProjectDocumentService projectDocumentService;

    @GetMapping
    @Operation(summary = "List all documents in a project (members only)")
    public ResponseEntity<ApiResponseDto<List<ProjectDocumentResponse>>> listDocuments(
            @PathVariable UUID projectId) {
        List<ProjectDocumentResponse> docs = projectDocumentService.listDocuments(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Documents retrieved", docs));
    }

    @GetMapping("/embeddable")
    @Operation(summary = "List AI query-ready documents in a project (members only)")
    public ResponseEntity<ApiResponseDto<List<ProjectDocumentResponse>>> listEmbeddableDocuments(
            @PathVariable UUID projectId) {
        List<ProjectDocumentResponse> docs = projectDocumentService.listEmbeddableDocuments(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "AI query-ready documents retrieved", docs));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document to the project (members only)")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> uploadDocument(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(defaultValue = "false") boolean allowEmbedded,
            @RequestPart("file") MultipartFile file) throws Exception {
        ProjectDocumentResponse doc = projectDocumentService.uploadDocument(projectId, folderId, file, allowEmbedded);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Document uploaded", doc));
    }

    @PostMapping("/folders")
    @Operation(summary = "Create a project folder")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> createFolder(
            @PathVariable UUID projectId,
            @RequestBody com.iems.documentservice.dto.request.CreateFolderRequest request) {
        ProjectDocumentResponse doc = projectDocumentService.createFolder(projectId, request.getName(), request.getParentId());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder created", doc));
    }

    @PostMapping("/folders/init-default")
    @Operation(summary = "Initialize default docs folder for project")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> initDefaultDocsFolder(
            @PathVariable UUID projectId) {
        ProjectDocumentResponse doc = projectDocumentService.initDefaultDocsFolder(projectId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Default docs folder initialized", doc));
    }

    @PutMapping("/{docId}/rename")
    @Operation(summary = "Rename a project document/folder")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> renameDocument(
            @PathVariable UUID projectId,
            @PathVariable UUID docId,
            @RequestBody com.iems.documentservice.dto.request.RenameRequest request) {
        ProjectDocumentResponse doc = projectDocumentService.renameDocument(projectId, docId, request.getName());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Document renamed", doc));
    }

    @PutMapping("/{docId}/move")
    @Operation(summary = "Move a project document/folder")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> moveDocument(
            @PathVariable UUID projectId,
            @PathVariable UUID docId,
            @RequestParam(required = false) UUID parentId) {
        ProjectDocumentResponse doc = projectDocumentService.moveDocument(projectId, docId, parentId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Document moved", doc));
    }

    @PutMapping("/{docId}/allow-embedded")
    @Operation(summary = "Enable or disable AI embedding for a file")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> setAllowEmbedded(
            @PathVariable UUID projectId,
            @PathVariable UUID docId,
            @RequestParam boolean allowEmbedded) {
        ProjectDocumentResponse doc = projectDocumentService.setAllowEmbedded(projectId, docId, allowEmbedded);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Embed setting updated", doc));
    }

    @DeleteMapping("/{docId}")
    @Operation(summary = "Delete a project document (uploader only)")
    public ResponseEntity<ApiResponseDto<Object>> deleteDocument(
            @PathVariable UUID projectId,
            @PathVariable UUID docId) throws Exception {
        projectDocumentService.deleteDocument(projectId, docId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Document deleted", null));
    }

    @GetMapping("/{docId}/link")
    @Operation(summary = "Get a presigned download link for a document (members only)")
    public ResponseEntity<ApiResponseDto<ProjectDocumentResponse>> getDownloadLink(
            @PathVariable UUID projectId,
            @PathVariable UUID docId) throws Exception {
        ProjectDocumentResponse doc = projectDocumentService.getDownloadLink(projectId, docId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Download link generated", doc));
    }
}
