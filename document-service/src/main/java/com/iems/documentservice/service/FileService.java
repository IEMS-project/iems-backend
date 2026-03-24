package com.iems.documentservice.service;

import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.SearchResultItem;
import com.iems.documentservice.dto.response.SimpleFileResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ShareRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024; // 50 MB

    private final StoredFileRepository storedFileRepository;
    private final FolderRepository folderRepository;
    private final ShareRepository shareRepository;
    private final FavoriteRepository favoriteRepository;
    private final ObjectStorageService storageService;
    private final PermissionHelper permissionHelper;

    public FileService(StoredFileRepository storedFileRepository,
                       FolderRepository folderRepository,
                       ShareRepository shareRepository,
                       FavoriteRepository favoriteRepository,
                       ObjectStorageService storageService,
                       PermissionHelper permissionHelper) {
        this.storedFileRepository = storedFileRepository;
        this.folderRepository = folderRepository;
        this.shareRepository = shareRepository;
        this.favoriteRepository = favoriteRepository;
        this.storageService = storageService;
        this.permissionHelper = permissionHelper;
    }

    // ──────────────────────────── UPLOAD ────────────────────────────

    @Transactional
    public FileResponse uploadFile(UUID folderId, MultipartFile file) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        permissionHelper.enforceWritePermission(folderId, ownerId);
        validateSize(file.getSize(), MAX_UPLOAD_SIZE);
        Folder folder = resolveFolder(folderId);
        String objectKey = buildObjectKey(folderId, ownerId, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), file.getContentType());
        }
        StoredFile saved = storedFileRepository.save(StoredFile.builder()
                .name(file.getOriginalFilename())
                .folder(folder)
                .ownerId(ownerId)
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(Permission.PUBLIC)
                .createdAt(OffsetDateTime.now())
                .build());
        return toResponse(saved, null, ownerId);
    }

    @Transactional
    public List<SimpleFileResponse> uploadBatch(UUID folderId, MultipartFile[] files) throws Exception {
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile f : files) {
            FileResponse fr = uploadFile(folderId, f);
            results.add(SimpleFileResponse.builder()
                    .id(fr.getId().toString())
                    .fileName(fr.getName())
                    .url(storageService.buildPublicUrl(fr.getPath()))
                    .type(fr.getType())
                    .build());
        }
        return results;
    }

    @Transactional
    public List<SimpleFileResponse> uploadChatFiles(String conversationId, MultipartFile[] files) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            validateSize(file.getSize(), MAX_UPLOAD_SIZE);
            double sizeMb = file.getSize() / (1024.0 * 1024.0);
            String mime = file.getContentType() == null ? "" : file.getContentType();
            if (mime.startsWith("image") && sizeMb > 5.0) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            if (!mime.startsWith("image") && sizeMb > 20.0) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            String objectKey = buildChatObjectKey(conversationId, file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                storageService.upload(objectKey, in, file.getSize(), file.getContentType());
            }
            StoredFile saved = storedFileRepository.save(StoredFile.builder()
                    .name(file.getOriginalFilename())
                    .folder(null)
                    .ownerId(ownerId)
                    .path(objectKey)
                    .size(file.getSize())
                    .type(file.getContentType())
                    .permission(Permission.PUBLIC)
                    .createdAt(OffsetDateTime.now())
                    .build());
            results.add(SimpleFileResponse.builder()
                    .id(saved.getId().toString())
                    .fileName(saved.getName())
                    .url(storageService.buildPublicUrl(objectKey))
                    .type(saved.getType())
                    .build());
        }
        return results;
    }

    @Transactional
    public List<SimpleFileResponse> uploadPublicFiles(MultipartFile[] files) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            validateSize(file.getSize(), MAX_UPLOAD_SIZE);
            String objectKey = buildPublicObjectKey(file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                storageService.upload(objectKey, in, file.getSize(), file.getContentType());
            }
            StoredFile saved = storedFileRepository.save(StoredFile.builder()
                    .name(file.getOriginalFilename())
                    .folder(null)
                    .ownerId(ownerId)
                    .path(objectKey)
                    .size(file.getSize())
                    .type(file.getContentType())
                    .permission(Permission.PUBLIC)
                    .createdAt(OffsetDateTime.now())
                    .build());
            results.add(SimpleFileResponse.builder()
                    .id(saved.getId().toString())
                    .fileName(saved.getName())
                    .url(storageService.buildPublicUrl(objectKey))
                    .type(saved.getType())
                    .build());
        }
        return results;
    }

    // ──────────────────────────── DOWNLOAD ────────────────────────────

    public FileResponse downloadInfo(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceReadPermission(file, userId);
        String presignedUrl = storageService.presignGetUrl(file.getPath());
        return toResponse(file, presignedUrl, userId);
    }

    public InputStream downloadStream(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceReadPermission(file, userId);
        return storageService.download(file.getPath());
    }

    // ──────────────────────────── LIST ────────────────────────────

    public List<FileResponse> listFiles() {
        UUID userId = permissionHelper.getCurrentUserId();
        return storedFileRepository.findByOwnerIdAndDeletedAtIsNull(userId).stream()
                .filter(f -> !isAvatarFile(f))
                .map(f -> toResponse(f, null, userId))
                .collect(Collectors.toList());
    }

    public List<FileResponse> listFilesInFolder(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        // kiểm tra quyền truy cập folder
        if (!folder.getOwnerId().equals(userId)
                && folder.getPermission() != Permission.PUBLIC
                && !shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", userId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        return storedFileRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
                .filter(f -> !isAvatarFile(f))
                .filter(f -> {
                    try { permissionHelper.enforceReadPermission(f, userId); return true; }
                    catch (Exception e) { return false; }
                })
                .map(f -> toResponse(f, null, userId))
                .collect(Collectors.toList());
    }

    public List<FileResponse> listAccessibleFiles() {
        UUID userId = permissionHelper.getCurrentUserId();
        List<StoredFile> owned = storedFileRepository.findByOwnerId(userId);
        List<StoredFile> publicFiles = storedFileRepository.findByPermission(Permission.PUBLIC).stream()
                .filter(f -> !isAvatarFile(f))
                .collect(Collectors.toList());
        Set<UUID> sharedFileIds = shareRepository.findBySharedWithUserId(userId).stream()
                .filter(s -> "FILE".equals(s.getTargetType()))
                .map(Share::getTargetId)
                .collect(Collectors.toSet());
        List<StoredFile> sharedFiles = sharedFileIds.isEmpty() ? List.of()
                : storedFileRepository.findByIdIn(sharedFileIds);
        Set<UUID> seen = new HashSet<>();
        return List.of(owned, publicFiles, sharedFiles).stream()
                .flatMap(List::stream)
                .filter(f -> !isAvatarFile(f))
                .filter(f -> seen.add(f.getId()))
                .map(f -> toResponse(f, null, userId))
                .collect(Collectors.toList());
    }

    // ──────────────────────────── SEARCH ────────────────────────────

    public List<SearchResultItem> searchFiles(String query) {
        UUID userId = permissionHelper.getCurrentUserId();
        String q = query == null ? "" : query.toLowerCase();
        return storedFileRepository.findByOwnerId(userId).stream()
                .filter(f -> !isAvatarFile(f))
                .filter(f -> f.getName() != null && f.getName().toLowerCase().contains(q))
                .map(f -> SearchResultItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FILE")
                        .parentId(f.getFolder() != null ? f.getFolder().getId() : null)
                        .size(f.getSize())
                        .mimeType(f.getType())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ──────────────────────────── RENAME / MOVE / PERMISSION ────────────────────────────

    @Transactional
    public void rename(UUID fileId, String newName) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        file.setName(newName);
        storedFileRepository.save(file);
    }

    @Transactional
    public void move(UUID fileId, UUID newFolderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        permissionHelper.enforceWritePermission(newFolderId, userId);
        file.setFolder(newFolderId != null ? folderRepository.findById(newFolderId).orElse(null) : null);
        storedFileRepository.save(file);
    }

    @Transactional
    public void updatePermission(UUID fileId, Permission permission) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        file.setPermission(permission);
        storedFileRepository.save(file);
    }

    // ──────────────────────────── HELPERS ────────────────────────────

    /** Xóa vĩnh viễn file ngay lập tức (dùng trong batchDelete) — không cần file ở trong thùng rác */
    @Transactional
    public void forceDelete(UUID fileId, UUID requesterId) throws Exception {
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), requesterId);
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    public StoredFile getOrThrow(UUID fileId) {
        return storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
    }

    public StoredFileRepository getRepository() {
        return storedFileRepository;
    }

    public FileResponse toResponse(StoredFile file, String presignedUrl, UUID userId) {
        return FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .ownerId(file.getOwnerId())
                .path(file.getPath())
                .size(file.getSize())
                .type(file.getType())
                .permission(file.getPermission())
                .createdAt(file.getCreatedAt())
                .presignedUrl(presignedUrl)
                .favorite(favoriteRepository.findByUserIdAndTargetId(userId, file.getId()).isPresent())
                .build();
    }

    private Folder resolveFolder(UUID folderId) {
        if (folderId == null) return null;
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
    }

    private void validateSize(long size, long max) {
        if (size > max) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
    }

    /** Lọc bỏ file avatar khỏi danh sách document */
    private boolean isAvatarFile(StoredFile f) {
        String path = f.getPath();
        return path != null && (path.startsWith("avatar/employee/") || path.startsWith("avatar/group/"));
    }

    private String buildObjectKey(UUID folderId, UUID ownerId, String fileName) {
        String folderPart = folderId != null ? String.valueOf(folderId) : "root";
        return "document/owners/" + ownerId + "/" + folderPart + "/" + System.currentTimeMillis() + "-" + fileName;
    }

    private String buildChatObjectKey(String conversationId, String fileName) {
        return "chat/" + conversationId + "/" + System.currentTimeMillis() + "-" + fileName;
    }

    private String buildPublicObjectKey(String fileName) {
        return "document/public/" + System.currentTimeMillis() + "-" + fileName;
    }
}
