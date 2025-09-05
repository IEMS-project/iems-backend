package com.iems.documentservice.controller;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.UpdatePermissionRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.response.ApiResponseDto;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.FolderResponse;
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

@RestController
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

    @PostMapping("/files/{id}/share")
    @Operation(summary = "Share file with users")
    public ResponseEntity<ApiResponseDto<Object>> share(@PathVariable Long id, @Valid @RequestBody ShareRequest request) {
        driveService.shareFile(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Shared", null));
    }

    @PostMapping("/files/{id}/unshare")
    @Operation(summary = "Unshare file with users")
    public ResponseEntity<ApiResponseDto<Object>> unshare(@PathVariable Long id, @Valid @RequestBody ShareRequest request) {
        driveService.unshareFile(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Unshared", null));
    }

    @GetMapping("/folders")
    @Operation(summary = "List all folders of owner")
    public ResponseEntity<ApiResponseDto<Object>> listFolders(@RequestParam Long ownerId) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFolders(ownerId)));
    }

    @GetMapping("/files")
    @Operation(summary = "List all files of owner")
    public ResponseEntity<ApiResponseDto<Object>> listFiles(@RequestParam Long ownerId) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFiles(ownerId)));
    }

    @GetMapping("/files/accessible")
    @Operation(summary = "List all files accessible to requester (owned/public/shared)")
    public ResponseEntity<ApiResponseDto<Object>> listAccessibleFiles(@RequestParam Long requesterId) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listAccessibleFiles(requesterId)));
    }

    @GetMapping("/folders/{id}/files")
    @Operation(summary = "List files inside a folder")
    public ResponseEntity<ApiResponseDto<Object>> listFilesInFolder(@PathVariable Long id, @RequestParam Long ownerId) {
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", driveService.listFilesInFolder(id, ownerId)));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file to folder")
    public ResponseEntity<ApiResponseDto<FileResponse>> uploadFile(@RequestParam(required = false) Long folderId,
                                                                    @RequestParam Long ownerId,
                                                                    @RequestPart("file") MultipartFile file) throws Exception {
        FileResponse data = driveService.uploadFile(folderId, ownerId, file);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File uploaded", data));
    }

    @GetMapping("/files/{id}/download")
    @Operation(summary = "Download file stream")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam Long requesterId) throws Exception {
        try (InputStream in = driveService.downloadStream(id, requesterId)) {
            byte[] bytes = in.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        }
    }

    @GetMapping("/files/{id}/link")
    @Operation(summary = "Generate presigned link to share")
    public ResponseEntity<ApiResponseDto<FileResponse>> presign(@PathVariable Long id, @RequestParam Long requesterId) throws Exception {
        FileResponse data = driveService.downloadInfo(id, requesterId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Presigned link generated", data));
    }

    @PatchMapping("/files/{id}/permission")
    @Operation(summary = "Update permission of a file")
    public ResponseEntity<ApiResponseDto<Object>> updatePermission(@PathVariable Long id,
                                                                   @Valid @RequestBody UpdatePermissionRequest request) {
        driveService.updatePermission(id, request);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Permission updated", null));
    }

    @DeleteMapping("/files/{id}")
    @Operation(summary = "Delete file")
    public ResponseEntity<ApiResponseDto<Object>> deleteFile(@PathVariable Long id, @RequestParam Long requesterId) throws Exception {
        driveService.deleteFile(id, requesterId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "File deleted", null));
    }

    @DeleteMapping("/folders/{id}")
    @Operation(summary = "Delete folder recursively")
    public ResponseEntity<ApiResponseDto<Object>> deleteFolder(@PathVariable Long id, @RequestParam Long requesterId) throws Exception {
        driveService.deleteFolderRecursive(id, requesterId);
        return ResponseEntity.ok(new ApiResponseDto<>(200, "Folder deleted", null));
    }
}


