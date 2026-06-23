package com.iems.documentservice.controller;

import com.iems.documentservice.dto.response.ApiResponseDto;
import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.UpdateAvatarRequest;
import com.iems.documentservice.client.ChatServiceFeignClient;
import com.iems.documentservice.client.ProjectServiceFeignClient;
import com.iems.documentservice.dto.request.UpdateGroupAvatarRequest;
import com.iems.documentservice.dto.request.UpdateProjectAvatarRequest;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.repository.StoredFileRepository;
import com.iems.documentservice.security.JwtUserDetails;
import com.iems.documentservice.service.ObjectStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Avatar API", description = "Employee avatar upload and retrieval")
public class AvatarController {

    private static final Pattern UNSAFE_PATH_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final ObjectStorageService storageService;
    private final StoredFileRepository storedFileRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final ChatServiceFeignClient chatServiceFeignClient;
    private final ProjectServiceFeignClient projectServiceFeignClient;

    public AvatarController(ObjectStorageService storageService, StoredFileRepository storedFileRepository, UserServiceFeignClient userServiceFeignClient, ChatServiceFeignClient chatServiceFeignClient, ProjectServiceFeignClient projectServiceFeignClient) {
        this.storageService = storageService;
        this.storedFileRepository = storedFileRepository;
        this.userServiceFeignClient = userServiceFeignClient;
        this.chatServiceFeignClient = chatServiceFeignClient;
        this.projectServiceFeignClient = projectServiceFeignClient;
    }

    @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload employee avatar for current user")
    public ResponseEntity<ApiResponseDto<String>> uploadAvatar(@RequestPart("file") MultipartFile file) throws Exception {
        validateUpload(file);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails principal = (JwtUserDetails) authentication.getPrincipal();
        UUID userId = principal.getUserId();

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1 && dot < original.length() - 1) {
            ext = original.substring(dot);
        }
        String fileName = userId + "_avt" + ext;
        String objectKey = "avatar/employee/" + fileName;

        storageService.upload(objectKey, file);

        StoredFile stored = StoredFile.builder()
                .name(fileName)
                .folder(null)
                .ownerId(userId)
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(Permission.PRIVATE)
                .createdAt(OffsetDateTime.now())
                .build();
        storedFileRepository.save(stored);

        String presignedUrl = storageService.buildPublicUrl(objectKey);

        // Call user-service to update my avatar URL (Authorization header forwarded by Feign interceptor)
        userServiceFeignClient.updateMyAvatar(new UpdateAvatarRequest(presignedUrl));

        return ResponseEntity.ok(new ApiResponseDto<>(200, "Avatar uploaded", presignedUrl));
    }

    @PostMapping(value = "/upload/group-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload group avatar for a specific group")
    public ResponseEntity<ApiResponseDto<String>> uploadGroupAvatar(@RequestPart("file") MultipartFile file,
                                                                    @RequestParam("groupId") String groupId) throws Exception {
        validateUpload(file);
        // Auth required; only admin or group owner allowed; Chat-Service will re-check on update call
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails principal = (JwtUserDetails) authentication.getPrincipal();

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1 && dot < original.length() - 1) {
            ext = original.substring(dot);
        }
        String safeGroupId = safePathSegment(groupId);
        String fileName = safeGroupId + "_avt" + ext;
        String objectKey = "avatar/group/" + fileName;

        storageService.upload(objectKey, file);

        StoredFile stored = StoredFile.builder()
                .name(fileName)
                .folder(null)
                .ownerId(principal.getUserId())
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(Permission.PRIVATE)
                .createdAt(OffsetDateTime.now())
                .build();
        storedFileRepository.save(stored);

        String presignedUrl = storageService.buildPublicUrl(objectKey);

        // Notify chat-service to update the group avatar URL
        chatServiceFeignClient.updateGroupAvatar(groupId, new UpdateGroupAvatarRequest(presignedUrl));

        return ResponseEntity.ok(new ApiResponseDto<>(200, "Group avatar uploaded", presignedUrl));
    }

    @PostMapping(value = "/upload/project-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload project avatar for a specific project")
    public ResponseEntity<ApiResponseDto<String>> uploadProjectAvatar(@RequestPart("file") MultipartFile file,
                                                                      @RequestParam("projectId") UUID projectId) throws Exception {
        validateUpload(file);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails principal = (JwtUserDetails) authentication.getPrincipal();

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1 && dot < original.length() - 1) {
            ext = original.substring(dot);
        }
        String fileName = projectId + "_avt" + ext;
        String objectKey = "avatar/project/" + fileName;

        storageService.upload(objectKey, file);

        StoredFile stored = StoredFile.builder()
                .name(fileName)
                .folder(null)
                .ownerId(principal.getUserId())
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(Permission.PRIVATE)
                .createdAt(OffsetDateTime.now())
                .build();
        storedFileRepository.save(stored);

        String publicUrl = storageService.buildPublicUrl(objectKey);
        projectServiceFeignClient.updateProjectAvatar(projectId, new UpdateProjectAvatarRequest(publicUrl));

        return ResponseEntity.ok(new ApiResponseDto<>(200, "Project avatar uploaded", publicUrl));
    }

    @GetMapping("/project-avatar/{projectId}")
    @Operation(summary = "Get project avatar URL")
    public ResponseEntity<ApiResponseDto<String>> getProjectAvatarUrl(@PathVariable("projectId") UUID projectId) throws Exception {
        Optional<StoredFile> latest = storedFileRepository
                .findFirstByPathStartingWithOrderByCreatedAtDesc("avatar/project/" + projectId + "_avt");

        if (latest.isEmpty()) {
            return ResponseEntity.ok(new ApiResponseDto<>(200, "No project avatar", null));
        }

        String presignedUrl = storageService.presignGetUrl(latest.get().getPath());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", presignedUrl));
    }

    @GetMapping("/group-avatar/{groupId}")
    @Operation(summary = "Get group avatar URL or stream if necessary")
    public ResponseEntity<ApiResponseDto<String>> getGroupAvatarUrl(@PathVariable("groupId") String groupId) throws Exception {
        // Find latest file that matches naming scheme
        String safeGroupId = safePathSegment(groupId);
        Optional<StoredFile> latest = storedFileRepository
                .findFirstByPathStartingWithOrderByCreatedAtDesc("avatar/group/" + safeGroupId + "_avt");

        if (latest.isEmpty()) {
            return ResponseEntity.ok(new ApiResponseDto<>(200, "No group avatar", null));
        }

        String presignedUrl = storageService.buildPublicUrl(latest.get().getPath());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", presignedUrl));
    }

    @GetMapping("/avatar/{userId}")
    @Operation(summary = "Get avatar URL for user or stream if necessary")
    public ResponseEntity<ApiResponseDto<String>> getAvatarUrl(@PathVariable("userId") UUID userId) throws Exception {
        Optional<StoredFile> latest = storedFileRepository
                .findFirstByOwnerIdAndPathStartingWithOrderByCreatedAtDesc(userId, "avatar/employee/" + userId + "_avt");

        if (latest.isEmpty()) {
            return ResponseEntity.ok(new ApiResponseDto<>(200, "No avatar", null));
        }

        String presignedUrl = storageService.buildPublicUrl(latest.get().getPath());
        return ResponseEntity.ok(new ApiResponseDto<>(200, "OK", presignedUrl));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is required");
        }
    }

    private String safePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return UNSAFE_PATH_CHARS.matcher(value).replaceAll("_");
    }
}


