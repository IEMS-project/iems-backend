package com.iems.documentservice.service;

import com.iems.documentservice.client.ProjectServiceFeignClient;
import com.iems.documentservice.dto.response.ProjectDocumentResponse;
import com.iems.documentservice.entity.ProjectDocument;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.ProjectDocumentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDocumentService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024; // 50 MB

    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectServiceFeignClient projectServiceFeignClient;
    private final ObjectStorageService objectStorageService;
    private final PermissionHelper permissionHelper;

    // ────────── ACCESS CHECK ──────────

    /**
     * Verify the current JWT user is a member of the project.
     * Throws PERMISSION_DENIED (403) if not.
     */
    private void requireProjectMember(UUID projectId) {
        try {
            projectServiceFeignClient.checkMembership(projectId);
        } catch (FeignException.Forbidden | FeignException.Unauthorized e) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        } catch (FeignException e) {
            log.warn("Membership check failed for project {}: {}", projectId, e.getMessage());
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    // ────────── LIST ──────────

    public List<ProjectDocumentResponse> listDocuments(UUID projectId) {
        requireProjectMember(projectId);
        return projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ────────── FOLDER OPERATIONS ──────────

    @Transactional
    public ProjectDocumentResponse createFolder(UUID projectId, String name, UUID parentId) {
        requireProjectMember(projectId);
        UUID userId = permissionHelper.getCurrentUserId();

        ProjectDocument doc = projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .fileName(name)
                .isFolder(true)
                .parentId(parentId)
                .uploadedBy(userId)
                .createdAt(OffsetDateTime.now())
                .build());

        return toResponse(doc);
    }

    @Transactional
    public ProjectDocumentResponse renameDocument(UUID projectId, UUID docId, String newName) {
        requireProjectMember(projectId);
        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        doc.setFileName(newName);
        return toResponse(projectDocumentRepository.save(doc));
    }

    @Transactional
    public ProjectDocumentResponse moveDocument(UUID projectId, UUID docId, UUID targetParentId) {
        requireProjectMember(projectId);
        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        doc.setParentId(targetParentId);
        return toResponse(projectDocumentRepository.save(doc));
    }

    // ────────── UPLOAD ──────────

    @Transactional
    public ProjectDocumentResponse uploadDocument(UUID projectId, UUID folderId, MultipartFile file) throws Exception {
        requireProjectMember(projectId);

        UUID userId = permissionHelper.getCurrentUserId();

        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }

        String objectKey = buildProjectObjectKey(projectId, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            objectStorageService.upload(objectKey, in, file.getSize(), file.getContentType());
        }

        ProjectDocument doc = projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .parentId(folderId)
                .fileId(UUID.randomUUID()) // internal tracking id
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .uploadedBy(userId)
                .cloudinaryPath(objectKey)
                .createdAt(OffsetDateTime.now())
                .build());

        return toResponse(doc);
    }

    // ────────── DELETE ──────────

    @Transactional
    public void deleteDocument(UUID projectId, UUID docId) throws Exception {
        requireProjectMember(projectId);

        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        UUID userId = permissionHelper.getCurrentUserId();
        // Only uploader can delete (members can delete their own uploads)
        if (!doc.getUploadedBy().equals(userId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }

        deleteRecursive(projectId, doc);
    }

    private void deleteRecursive(UUID projectId, ProjectDocument doc) throws Exception {
        if (Boolean.TRUE.equals(doc.getIsFolder())) {
            List<ProjectDocument> children = projectDocumentRepository.findByProjectIdAndParentId(projectId, doc.getId());
            for (ProjectDocument child : children) {
                deleteRecursive(projectId, child);
            }
        } else {
            if (doc.getCloudinaryPath() != null) {
                objectStorageService.delete(doc.getCloudinaryPath());
            }
        }
        projectDocumentRepository.delete(doc);
    }

    // ────────── DOWNLOAD LINK ──────────

    public ProjectDocumentResponse getDownloadLink(UUID projectId, UUID docId) throws Exception {
        requireProjectMember(projectId);

        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        String presignedUrl = objectStorageService.presignGetUrl(doc.getCloudinaryPath());
        return toResponse(doc, presignedUrl);
    }

    // ────────── HELPERS ──────────

    private ProjectDocumentResponse toResponse(ProjectDocument doc) {
        return toResponse(doc, null);
    }

    private ProjectDocumentResponse toResponse(ProjectDocument doc, String downloadUrl) {
        return ProjectDocumentResponse.builder()
                .id(doc.getId())
                .projectId(doc.getProjectId())
                .fileId(doc.getFileId())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .fileType(doc.getFileType())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .downloadUrl(downloadUrl)
                .isFolder(doc.getIsFolder())
                .parentId(doc.getParentId())
                .build();
    }

    private String buildProjectObjectKey(UUID projectId, String fileName) {
        return "document/projects/" + projectId + "/" + System.currentTimeMillis() + "-" + fileName;
    }
}
