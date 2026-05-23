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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDocumentService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;
    private static final String DEFAULT_DOCS_FOLDER_NAME = "docs";
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectServiceFeignClient projectServiceFeignClient;
    private final ObjectStorageService objectStorageService;
    private final PermissionHelper permissionHelper;
    private final AiIndexingService aiIndexingService;
    private final ActivityService activityService;

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

        activityService.log("FOLDER", doc.getId(), "documents.activity.item.created",
                java.util.Map.of("itemName", name));

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
                    activityService.log("FOLDER", docsFolder.getId(), "documents.activity.item.created",
                            java.util.Map.of("itemName", DEFAULT_DOCS_FOLDER_NAME));
                    return toResponse(docsFolder);
                });
    }

    @Transactional
    public ProjectDocumentResponse renameDocument(UUID projectId, UUID docId, String newName) {
        requireProjectMember(projectId);
        ProjectDocument doc = projectDocumentRepository.findByProjectIdAndId(projectId, docId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));

        doc.setFileName(newName);
        ProjectDocument saved = projectDocumentRepository.save(doc);
        activityService.log(doc.getIsFolder() ? "FOLDER" : "FILE", doc.getId(),
                "documents.activity.item.renamed", java.util.Map.of("newName", newName));
        return toResponse(saved);
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
        ProjectDocument saved = projectDocumentRepository.save(doc);
        activityService.log(doc.getIsFolder() ? "FOLDER" : "FILE", doc.getId(),
                "documents.activity.item.moved");
        return toResponse(saved);
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
        if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }

        String objectKey = buildProjectObjectKey(projectId, file.getOriginalFilename());
        objectStorageService.upload(objectKey, file);

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

        activityService.log("FILE", doc.getId(), "documents.activity.item.created",
                java.util.Map.of("itemName", file.getOriginalFilename()));

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

        activityService.log(doc.getIsFolder() ? "FOLDER" : "FILE", doc.getId(),
                "documents.activity.item.deleted");
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
        aiIndexingService.dispatchIndex(doc);
    }

    private void dispatchDeindex(ProjectDocument doc) {
        aiIndexingService.dispatchDeindex(doc);
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
        return "document/projects/" + projectId + "/" + UUID.randomUUID() + "-" + safeFileName(fileName);
    }

    private String safeFileName(String fileName) {
        String normalized = fileName == null ? "file" : fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = UNSAFE_FILE_NAME_CHARS.matcher(normalized).replaceAll("_");
        return normalized.isBlank() ? "file" : normalized;
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

    public List<com.iems.documentservice.dto.response.DocumentActivityResponse> listActivities(UUID docId, String type) {
        return activityService.listActivities(docId, type);
    }
}
