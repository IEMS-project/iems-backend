package com.iems.documentservice.service;

import com.iems.documentservice.client.AiServiceFeignClient;
import com.iems.documentservice.client.ProjectServiceFeignClient;
import com.iems.documentservice.dto.request.AiIndexCommandRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDocumentService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;
    private static final String DEFAULT_DOCS_FOLDER_NAME = "docs";

    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectServiceFeignClient projectServiceFeignClient;
    private final ObjectStorageService objectStorageService;
    private final PermissionHelper permissionHelper;
    private final AiServiceFeignClient aiServiceFeignClient;

    private void requireProjectMember(UUID projectId) {
        try {
            projectServiceFeignClient.checkMembership(projectId);
        } catch (FeignException e) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    public List<ProjectDocumentResponse> listDocuments(UUID projectId) {
        requireProjectMember(projectId);
        return projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProjectDocumentResponse> listEmbeddableDocuments(UUID projectId) {
        requireProjectMember(projectId);
        return projectDocumentRepository
                .findByProjectIdAndAllowEmbeddedTrueAndAiIndexedTrueAndIsFolderFalseOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

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
    public ProjectDocumentResponse initDefaultDocsFolder(UUID projectId) {
        requireProjectMember(projectId);

        return projectDocumentRepository
                .findFirstByProjectIdAndIsFolderTrueAndParentIdIsNullAndFileNameIgnoreCase(projectId,
                        DEFAULT_DOCS_FOLDER_NAME)
                .map(this::toResponse)
                .orElseGet(() -> {
                    UUID userId = permissionHelper.getCurrentUserId();
                    ProjectDocument docsFolder = projectDocumentRepository.save(ProjectDocument.builder()
                            .projectId(projectId)
                            .fileName(DEFAULT_DOCS_FOLDER_NAME)
                            .isFolder(true)
                            .parentId(null)
                            .uploadedBy(userId)
                            .createdAt(OffsetDateTime.now())
                            .build());
                    return toResponse(docsFolder);
                });
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
    public ProjectDocumentResponse setAllowEmbedded(UUID projectId, UUID docId, boolean allowEmbedded) {
        requireProjectMember(projectId);
        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        if (Boolean.TRUE.equals(doc.getIsFolder())) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }

        boolean wasPreviouslyEmbedded = Boolean.TRUE.equals(doc.getAllowEmbedded());
        doc.setAllowEmbedded(allowEmbedded);
        doc.setAiIndexed(allowEmbedded && isRagSupported(doc));
        ProjectDocument savedDoc = projectDocumentRepository.save(doc);

        if (!wasPreviouslyEmbedded && allowEmbedded) {
            dispatchIndex(savedDoc);
        } else if (wasPreviouslyEmbedded && !allowEmbedded) {
            dispatchDeindex(savedDoc);
        }

        return toResponse(savedDoc);
    }

    @Transactional
    public ProjectDocumentResponse moveDocument(UUID projectId, UUID docId, UUID targetParentId) {
        requireProjectMember(projectId);
        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        doc.setParentId(targetParentId);
        return toResponse(projectDocumentRepository.save(doc));
    }

    @Transactional
    public ProjectDocumentResponse uploadDocument(UUID projectId,
            UUID folderId,
            MultipartFile file,
            boolean allowEmbedded) throws Exception {
        requireProjectMember(projectId);
        UUID userId = permissionHelper.getCurrentUserId();

        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }

        String objectKey = buildProjectObjectKey(projectId, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            objectStorageService.upload(objectKey, in, file.getSize(), file.getContentType());
        }

        boolean ragSupported = isRagSupported(file.getOriginalFilename(), file.getContentType());
        ProjectDocument doc = projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .parentId(folderId)
                .fileId(UUID.randomUUID())
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .uploadedBy(userId)
                .cloudinaryPath(objectKey)
                .createdAt(OffsetDateTime.now())
                .allowEmbedded(allowEmbedded)
                .aiIndexed(allowEmbedded && ragSupported)
                .build());

        log.info(
                "Uploaded document id={} projectId={} fileName={} fileType={} allowEmbedded={} ragSupported={} aiIndexed={}",
                doc.getId(),
                projectId,
                doc.getFileName(),
                doc.getFileType(),
                allowEmbedded,
                ragSupported,
                doc.getAiIndexed());

        if (allowEmbedded && ragSupported) {
            dispatchIndex(doc);
        } else if (allowEmbedded) {
            log.info("Skip INDEX dispatch for doc {} because file is not RAG supported", doc.getId());
        }

        return toResponse(doc);
    }

    @Transactional
    public void deleteDocument(UUID projectId, UUID docId) throws Exception {
        requireProjectMember(projectId);

        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        UUID userId = permissionHelper.getCurrentUserId();
        if (!doc.getUploadedBy().equals(userId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }

        deleteRecursive(projectId, doc);
    }

    public ProjectDocumentResponse getDownloadLink(UUID projectId, UUID docId) throws Exception {
        requireProjectMember(projectId);

        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        String presignedUrl = objectStorageService.presignGetUrl(doc.getCloudinaryPath());
        return toResponse(doc, presignedUrl);
    }

    private void deleteRecursive(UUID projectId, ProjectDocument doc) throws Exception {
        if (Boolean.TRUE.equals(doc.getIsFolder())) {
            List<ProjectDocument> children = projectDocumentRepository.findByProjectIdAndParentId(projectId,
                    doc.getId());
            for (ProjectDocument child : children) {
                deleteRecursive(projectId, child);
            }
        } else {
            if (Boolean.TRUE.equals(doc.getAiIndexed())) {
                dispatchDeindex(doc);
            }
            if (doc.getCloudinaryPath() != null) {
                objectStorageService.delete(doc.getCloudinaryPath());
            }
        }
        projectDocumentRepository.delete(doc);
    }

    private void dispatchIndex(ProjectDocument doc) {
        try {
            String downloadUrl = objectStorageService.presignGetUrl(doc.getCloudinaryPath());
            log.info("Dispatch INDEX docId={} projectId={} fileName={} fileType={} urlPresent={}",
                    doc.getId(),
                    doc.getProjectId(),
                    doc.getFileName(),
                    doc.getFileType(),
                    downloadUrl != null && !downloadUrl.isBlank());
            aiServiceFeignClient.dispatchIndexCommand(AiIndexCommandRequest.builder()
                    .projectId(doc.getProjectId().toString())
                    .documentId(doc.getId().toString())
                    .operation("INDEX")
                    .fileName(doc.getFileName())
                    .fileType(doc.getFileType())
                    .downloadUrl(downloadUrl)
                    .build());
            log.info("Dispatch INDEX completed for doc {}", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to dispatch INDEX for doc {}: {}", doc.getId(), e.getMessage());
        }
    }

    private void dispatchDeindex(ProjectDocument doc) {
        try {
            log.info("Dispatch DEINDEX docId={} projectId={}", doc.getId(), doc.getProjectId());
            aiServiceFeignClient.dispatchIndexCommand(AiIndexCommandRequest.builder()
                    .projectId(doc.getProjectId().toString())
                    .documentId(doc.getId().toString())
                    .operation("DEINDEX")
                    .build());
            log.info("Dispatch DEINDEX completed for doc {}", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to dispatch DEINDEX for doc {}: {}", doc.getId(), e.getMessage());
        }
    }

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
                .allowEmbedded(doc.getAllowEmbedded())
                .aiIndexed(doc.getAiIndexed())
                .build();
    }

    private String buildProjectObjectKey(UUID projectId, String fileName) {
        return "document/projects/" + projectId + "/" + System.currentTimeMillis() + "-" + fileName;
    }

    private boolean isRagSupported(ProjectDocument doc) {
        return isRagSupported(doc.getFileName(), doc.getFileType());
    }

    private boolean isRagSupported(String fileName, String fileType) {
        String normalizedFileName = fileName != null ? fileName.toLowerCase() : "";
        String normalizedFileType = fileType != null ? fileType.toLowerCase() : "";
        return normalizedFileName.endsWith(".txt")
                || normalizedFileName.endsWith(".pdf")
                || normalizedFileName.endsWith(".docx")
                || normalizedFileType.contains("text")
                || normalizedFileType.contains("pdf")
                || normalizedFileType.contains("wordprocessingml")
                || normalizedFileType.contains("json")
                || normalizedFileType.contains("xml")
                || normalizedFileType.contains("markdown");
    }
}
