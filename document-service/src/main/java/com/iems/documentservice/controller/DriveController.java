package com.iems.documentservice.controller;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.UpdatePermissionRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.request.RenameRequest;
import com.iems.documentservice.dto.request.UpdateSharePermissionRequest;
import com.iems.documentservice.dto.response.ApiResponseDto;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.dto.response.SearchResultItem;
import com.iems.documentservice.dto.response.FavoriteItemResponse;
import com.iems.documentservice.service.DriveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Drive API", description = "Google Drive-like file management")
public class DriveController {

    private final DriveService driveService;

    public DriveController(DriveService driveService) {
        this.driveService = driveService;
    }

    @PostMapping("/folders")
    @Operation(summary = "Create folder")
    public ResponseEntity<ApiResponseDto<FolderResponse>> createFolder(@Valid @RequestBody CreateFolderRequest request) {
        FolderResponse data = driveService.createFolder(request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder created", data));
    }


    @GetMapping("/folders")
    @Operation(summary = "List all folders of owner")
    public ResponseEntity<ApiResponseDto<Object>> listFolders() {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFolders()));
    }

    @GetMapping("/files")
    @Operation(summary = "List all files of owner")
    public ResponseEntity<ApiResponseDto<Object>> listFiles() {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFiles()));
    }

    @GetMapping("/files/accessible")
    @Operation(summary = "List all files accessible to requester (owned/public/shared)")
    public ResponseEntity<ApiResponseDto<Object>> listAccessibleFiles() {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listAccessibleFiles()));
    }

    @GetMapping("/folders/{id}/contents")
    @Operation(summary = "List all folders and files inside a folder")
    public ResponseEntity<ApiResponseDto<Object>> listFolderContents(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFolderContents(id)));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file to folder")
    public ResponseEntity<ApiResponseDto<FileResponse>> uploadFile(@RequestParam(required = false) UUID folderId,
                                                                    @RequestPart("file") MultipartFile file) throws Exception {
        FileResponse data = driveService.uploadFile(folderId, file);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File uploaded", data));
    }

    @GetMapping("/files/{id}/download")
    @Operation(summary = "Download file stream")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) throws Exception {
        try (InputStream in = driveService.downloadStream(id)) {
            byte[] bytes = in.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        }
    }

    @GetMapping("/files/{id}/link")
    @Operation(summary = "Generate presigned link to share")
    public ResponseEntity<ApiResponseDto<FileResponse>> presign(@PathVariable UUID id) throws Exception {
        FileResponse data = driveService.downloadInfo(id);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Presigned link generated", data));
    }

    @DeleteMapping("/files/{id}")
    @Operation(summary = "Delete file")
    public ResponseEntity<ApiResponseDto<Object>> deleteFile(@PathVariable UUID id) throws Exception {
        driveService.deleteFile(id);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File deleted", null));
    }

    @DeleteMapping("/folders/{id}")
    @Operation(summary = "Delete folder recursively")
    public ResponseEntity<ApiResponseDto<Object>> deleteFolder(@PathVariable UUID id) throws Exception {
        driveService.deleteFolderRecursive(id);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder deleted", null));
    }

    // Search
    @GetMapping("/search")
    @Operation(summary = "Search folders and files by name")
    public ResponseEntity<ApiResponseDto<Object>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.search(query)));
    }

    // Favorites
    @PostMapping("/favorites")
    @Operation(summary = "Favorite for folder or file")
    public ResponseEntity<ApiResponseDto<Object>> toggleFavorite(@RequestParam("id") UUID targetId, @RequestParam("type") String type) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.toggleFavorite(targetId, type)));
    }

    @GetMapping("/favorites")
    @Operation(summary = "List all favorites of current user")
    public ResponseEntity<ApiResponseDto<Object>> listFavorites() {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFavorites()));
    }

    // Unified share/unshare for both folders and files
    @PostMapping("/items/{id}/share")
    @Operation(summary = "Share folder or file with users")
    public ResponseEntity<ApiResponseDto<Object>> shareItem(@PathVariable UUID id, @RequestParam("type") String type, @Valid @RequestBody ShareRequest request) {
        driveService.shareItem(id, type, request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Shared", null));
    }

    @PostMapping("/items/{id}/unshare")
    @Operation(summary = "Unshare folder or file with users")
    public ResponseEntity<ApiResponseDto<Object>> unshareItem(@PathVariable UUID id, @RequestParam("type") String type, @Valid @RequestBody ShareRequest request) {
        driveService.unshareItem(id, type, request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Unshared", null));
    }

    // Update folder permission
    @PatchMapping("/folders/{id}/permission")
    @Operation(summary = "Update folder permission (PUBLIC/PRIVATE)")
    public ResponseEntity<ApiResponseDto<Object>> updateFolderPermission(@PathVariable UUID id, @RequestParam("permission") String permission) {
        driveService.updateFolderPermission(id, com.iems.documentservice.entity.enums.Permission.valueOf(permission));
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Permission updated", null));
    }

    // Rename folder
    @PatchMapping("/folders/{id}/rename")
    @Operation(summary = "Rename folder")
    public ResponseEntity<ApiResponseDto<Object>> renameFolder(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        driveService.renameFolder(id, request.getName());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder renamed", null));
    }

    // Rename file
    @PatchMapping("/files/{id}/rename")
    @Operation(summary = "Rename file")
    public ResponseEntity<ApiResponseDto<Object>> renameFile(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        driveService.renameFile(id, request.getName());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File renamed", null));
    }

    // Update file permission
    @PatchMapping("/files/{id}/permission")
    @Operation(summary = "Update file permission (PUBLIC/PRIVATE)")
    public ResponseEntity<ApiResponseDto<Object>> updateFilePermission(@PathVariable UUID id, @RequestParam("permission") String permission) {
        driveService.updateFilePermission(id, com.iems.documentservice.entity.enums.Permission.valueOf(permission));
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Permission updated", null));
    }

    // Get shared users
    @GetMapping("/items/{id}/shared-users")
    @Operation(summary = "Get shared users for an item")
    public ResponseEntity<ApiResponseDto<Object>> getSharedUsers(@PathVariable UUID id, @RequestParam("type") String type) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.getSharedUsers(id, type)));
    }

    // Update share permission
    @PatchMapping("/shares/{shareId}/permission")
    @Operation(summary = "Update share permission (VIEWER/EDITOR)")
    public ResponseEntity<ApiResponseDto<Object>> updateSharePermission(@PathVariable UUID shareId, @Valid @RequestBody UpdateSharePermissionRequest request) {
        driveService.updateSharePermission(shareId, request.getPermission());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Share permission updated", null));
    }

    // Remove share
    @DeleteMapping("/shares/{shareId}")
    @Operation(summary = "Remove share")
    public ResponseEntity<ApiResponseDto<Object>> removeShare(@PathVariable UUID shareId) {
        driveService.removeShare(shareId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Share removed", null));
    }

    // Move folder
    @PatchMapping("/folders/{folderId}/move")
    @Operation(summary = "Move folder to new parent")
    public ResponseEntity<ApiResponseDto<Object>> moveFolder(@PathVariable UUID folderId, @RequestParam(value = "parentId", required = false) UUID newParentId) {
        driveService.moveFolder(folderId, newParentId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder moved successfully", null));
    }

    // Move file
    @PatchMapping("/files/{fileId}/move")
    @Operation(summary = "Move file to new folder")
    public ResponseEntity<ApiResponseDto<Object>> moveFile(@PathVariable UUID fileId, @RequestParam(value = "folderId", required = false) UUID newFolderId) {
        driveService.moveFile(fileId, newFolderId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File moved successfully", null));
    }
}


