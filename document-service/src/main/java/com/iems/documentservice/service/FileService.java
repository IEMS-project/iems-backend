package com.iems.documentservice.service;

import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.AccountIdsDto;
import com.iems.documentservice.dto.request.RegisterFileMetadataRequest;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FileService {

    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final StoredFileRepository storedFileRepository;
    private final FolderRepository folderRepository;
    private final ShareRepository shareRepository;
    private final FavoriteRepository favoriteRepository;
    private final ObjectStorageService storageService;
    private final PermissionHelper permissionHelper;
    private final UserServiceFeignClient userServiceFeignClient;

    /**
     * Creates a new file service instance.
     *
     * @param storedFileRepository the stored file repository parameter
     * @param folderRepository the folder repository parameter
     * @param shareRepository the share repository parameter
     * @param favoriteRepository the favorite repository parameter
     * @param storageService the storage service parameter
     * @param permissionHelper the permission helper parameter
     * @param userServiceFeignClient the user service feign client parameter
     */
    public FileService(StoredFileRepository storedFileRepository,
                       FolderRepository folderRepository,
                       ShareRepository shareRepository,
                       FavoriteRepository favoriteRepository,
                       ObjectStorageService storageService,
                       PermissionHelper permissionHelper,
                       UserServiceFeignClient userServiceFeignClient) {
        this.storedFileRepository = storedFileRepository;
        this.folderRepository = folderRepository;
        this.shareRepository = shareRepository;
        this.favoriteRepository = favoriteRepository;
        this.storageService = storageService;
        this.permissionHelper = permissionHelper;
        this.userServiceFeignClient = userServiceFeignClient;
    }

    // ──────────────────────────── UPLOAD ────────────────────────────

    /**
     * Uploads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param file the file parameter
     * @return the upload file result
     * @throws Exception if the requested operation cannot be completed
     */
    public FileResponse uploadFile(UUID folderId, MultipartFile file) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        permissionHelper.enforceWritePermission(folderId, ownerId);
        validateFile(file);
        Folder folder = resolveFolder(folderId);
        String objectKey = buildObjectKey(folderId, ownerId, file.getOriginalFilename());
        storageService.upload(objectKey, file);
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

    /**
     * Uploads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     */
    public List<SimpleFileResponse> uploadBatch(UUID folderId, MultipartFile[] files) throws Exception {
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile f : files) {
            FileResponse fr = uploadFile(folderId, f);
            StoredFile savedFile = getOrThrow(fr.getId());
            results.add(SimpleFileResponse.builder()
                    .id(fr.getId().toString())
                    .fileName(fr.getName())
                    .url(storageService.buildPublicUrl(savedFile.getPath()))
                    .type(fr.getType())
                    .build());
        }
        return results;
    }

    /**
     * Generates file data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param fileName the file name parameter
     * @param contentType the content type parameter
     * @param folderId the folder id parameter
     * @return the generate upload signature result
     */
    public Map<String, Object> generateUploadSignature(String fileName, String contentType, UUID folderId) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        permissionHelper.enforceWritePermission(folderId, ownerId);
        
        long timestamp = System.currentTimeMillis() / 1000L;
        String folderPart = folderId != null ? String.valueOf(folderId) : "root";
        String objectKey = "document/owners/" + ownerId + "/" + folderPart + "/" + UUID.randomUUID() + "-" + safeFileName(fileName);
        
        return storageService.generateUploadSignature(objectKey, timestamp, contentType);
    }
    
    /**
     * Registers a new user account.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the register metadata result
     */
    @Transactional
    public FileResponse registerMetadata(RegisterFileMetadataRequest request) {
        UUID ownerId = permissionHelper.getCurrentUserId();
        permissionHelper.enforceWritePermission(request.getFolderId(), ownerId);
        Folder folder = resolveFolder(request.getFolderId());
        
        StoredFile saved = storedFileRepository.save(StoredFile.builder()
                .name(request.getFileName())
                .folder(folder)
                .ownerId(ownerId)
                .path(request.getObjectKey())
                .size(request.getFileSize())
                .type(request.getFileType())
                .permission(Permission.PUBLIC)
                .createdAt(OffsetDateTime.now())
                .build());
        
        return toResponse(saved, null, ownerId);
    }

    /**
     * Uploads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     * @throws AppException if a business rule prevents the requested operation
     */
    public List<SimpleFileResponse> uploadChatFiles(String conversationId, MultipartFile[] files) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            double sizeMb = file.getSize() / (1024.0 * 1024.0);
            String mime = file.getContentType() == null ? "" : file.getContentType();
            if (mime.startsWith("image") && sizeMb > 5.0) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            if (!mime.startsWith("image") && sizeMb > 20.0) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            String objectKey = buildChatObjectKey(conversationId, file.getOriginalFilename());
            storageService.upload(objectKey, file);
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

    /**
     * Uploads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     */
    public List<SimpleFileResponse> uploadPublicFiles(MultipartFile[] files) throws Exception {
        UUID ownerId = permissionHelper.getCurrentUserId();
        List<SimpleFileResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);
            String objectKey = buildPublicObjectKey(file.getOriginalFilename());
            storageService.upload(objectKey, file);
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

    /**
     * Downloads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @return the download info result
     * @throws Exception if the requested operation cannot be completed
     */
    public FileResponse downloadInfo(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceReadPermission(file, userId);
        String presignedUrl = storageService.presignGetUrl(file.getPath());
        return toResponse(file, presignedUrl, userId);
    }

    /**
     * Downloads file content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @return the download stream result
     * @throws Exception if the requested operation cannot be completed
     */
    public InputStream downloadStream(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceReadPermission(file, userId);
        return storageService.download(file.getPath());
    }

    // ──────────────────────────── LIST ────────────────────────────

    /**
     * Lists file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FileResponse> listFiles() {
        UUID userId = permissionHelper.getCurrentUserId();
        List<StoredFile> ownedFiles = storedFileRepository.findByOwnerIdAndDeletedAtIsNull(userId).stream()
                .filter(f -> !isAvatarFile(f))
                .toList();
        Set<UUID> favoriteIds = loadFavoriteIds(userId, ownedFiles);
        Set<UUID> ownerIds = ownedFiles.stream().map(StoredFile::getOwnerId).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(ownerIds);

        return ownedFiles.stream()
                .map(f -> toResponse(f, null, userId, favoriteIds, owners.get(f.getOwnerId())))
                .collect(Collectors.toList());
    }

    /**
     * Lists file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @return the matching result collection
     * @throws AppException if a business rule prevents the requested operation
     */
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
        List<StoredFile> visibleFiles = storedFileRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
                .filter(f -> !isAvatarFile(f))
                .filter(f -> {
                    try { permissionHelper.enforceReadPermission(f, userId); return true; }
                    catch (Exception e) { return false; }
                })
                .toList();
        Set<UUID> favoriteIds = loadFavoriteIds(userId, visibleFiles);
        Set<UUID> ownerIds = visibleFiles.stream().map(StoredFile::getOwnerId).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(ownerIds);

        return visibleFiles.stream()
                .map(f -> toResponse(f, null, userId, favoriteIds, owners.get(f.getOwnerId())))
                .collect(Collectors.toList());
    }

    /**
     * Lists file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FileResponse> listAccessibleFiles() {
        UUID userId = permissionHelper.getCurrentUserId();
        List<StoredFile> owned = storedFileRepository.findByOwnerIdAndDeletedAtIsNull(userId);
        List<StoredFile> publicFiles = storedFileRepository.findByPermissionAndDeletedAtIsNull(Permission.PUBLIC).stream()
                .filter(f -> !isAvatarFile(f))
                .collect(Collectors.toList());
        Set<UUID> sharedFileIds = shareRepository.findBySharedWithUserId(userId).stream()
                .filter(s -> "FILE".equals(s.getTargetType()))
                .map(Share::getTargetId)
                .collect(Collectors.toSet());
        List<StoredFile> sharedFiles = sharedFileIds.isEmpty() ? List.of()
                : storedFileRepository.findByIdIn(sharedFileIds).stream()
                        .filter(f -> f.getDeletedAt() == null)
                        .toList();
        Set<UUID> seen = new HashSet<>();
        List<StoredFile> accessibleFiles = List.of(owned, publicFiles, sharedFiles).stream()
                .flatMap(List::stream)
                .filter(f -> !isAvatarFile(f))
                .filter(f -> seen.add(f.getId()))
                .toList();
        Set<UUID> favoriteIds = loadFavoriteIds(userId, accessibleFiles);
        Set<UUID> ownerIds = accessibleFiles.stream().map(StoredFile::getOwnerId).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(ownerIds);

        return accessibleFiles.stream()
                .map(f -> toResponse(f, null, userId, favoriteIds, owners.get(f.getOwnerId())))
                .collect(Collectors.toList());
    }

    // ──────────────────────────── SEARCH ────────────────────────────

    /**
     * Searches file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param query the query parameter
     * @return the matching result collection
     */
    public List<SearchResultItem> searchFiles(String query) {
        UUID userId = permissionHelper.getCurrentUserId();
        String q = query == null ? "" : query.trim();
        return storedFileRepository.findByOwnerIdAndDeletedAtIsNullAndNameContainingIgnoreCase(userId, q).stream()
                .filter(f -> !isAvatarFile(f))
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

    /**
     * Performs rename for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param newName the new name parameter
     */
    @Transactional
    public void rename(UUID fileId, String newName) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        file.setName(newName);
        storedFileRepository.save(file);
    }

    /**
     * Performs move for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param newFolderId the new folder id parameter
     */
    @Transactional
    public void move(UUID fileId, UUID newFolderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        permissionHelper.enforceWritePermission(newFolderId, userId);
        file.setFolder(newFolderId != null ? folderRepository.findById(newFolderId).orElse(null) : null);
        storedFileRepository.save(file);
    }

    /**
     * Updates file data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void updatePermission(UUID fileId, Permission permission) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        file.setPermission(permission);
        storedFileRepository.save(file);
    }

    // ──────────────────────────── HELPERS ────────────────────────────

    /**
     * Returns tức for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param requesterId the requester id parameter
     * @return the tức result
     * @throws Exception if the requested operation cannot be completed
     */
    /** Xóa vĩnh viễn file ngay lập tức (dùng trong batchDelete) — không cần file ở trong thùng rác */
    @Transactional
    public void forceDelete(UUID fileId, UUID requesterId) throws Exception {
        StoredFile file = getOrThrow(fileId);
        permissionHelper.enforceFileOwnerOrFolderOwner(file, requesterId);
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    /**
     * Retrieves file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @return the get or throw result
     */
    public StoredFile getOrThrow(UUID fileId) {
        return storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
    }

    /**
     * Retrieves file information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the get repository result
     */
    public StoredFileRepository getRepository() {
        return storedFileRepository;
    }

    /**
     * Returns to response for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param file the file parameter
     * @param presignedUrl the presigned url parameter
     * @param userId the user id parameter
     * @return the to response result
     */
    public FileResponse toResponse(StoredFile file, String presignedUrl, UUID userId) {
        Map<String, Object> owner = null;
        if (file.getOwnerId() != null) {
            Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(List.of(file.getOwnerId()));
            owner = owners.get(file.getOwnerId());
        }
        return toResponse(file, presignedUrl, userId, loadFavoriteIds(userId, List.of(file)), owner);
    }

    /**
     * Returns to response for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param file the file parameter
     * @param presignedUrl the presigned url parameter
     * @param userId the user id parameter
     * @param favoriteIds the favorite ids parameter
     * @param owner the owner parameter
     * @return the to response result
     */
    private FileResponse toResponse(StoredFile file, String presignedUrl, UUID userId, Set<UUID> favoriteIds, Map<String, Object> owner) {
        var builder = FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .ownerId(file.getOwnerId())
                .size(file.getSize())
                .type(file.getType())
                .permission(file.getPermission())
                .createdAt(file.getCreatedAt())
                .presignedUrl(presignedUrl)
                .favorite(favoriteIds.contains(file.getId()));

        if (owner != null) {
            if (owner.get("firstName") != null) {
                builder.ownerName(owner.get("firstName").toString() + " " + (owner.get("lastName") != null ? owner.get("lastName").toString() : ""));
            }
            if (owner.get("email") != null) builder.ownerEmail(owner.get("email").toString());
            if (owner.get("image") != null) builder.ownerAvatar(owner.get("image").toString());
        }

        // Add breadcrumbs
        if (file.getFolder() != null) {
            builder.breadcrumbs(buildFileBreadcrumbs(file.getFolder()));
        } else {
            builder.breadcrumbs(new ArrayList<>());
        }

        return builder.build();
    }

    /**
     * Builds file data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param folder the folder parameter
     * @return the matching result collection
     */
    private List<FileResponse.BreadcrumbResponse> buildFileBreadcrumbs(Folder folder) {
        List<FileResponse.BreadcrumbResponse> crumbs = new ArrayList<>();
        Folder cur = folder;
        while (cur != null) {
            crumbs.add(0, FileResponse.BreadcrumbResponse.builder()
                    .id(cur.getId())
                    .name(cur.getName())
                    .build());
            cur = cur.getParent();
        }
        return crumbs;
    }

    /**
     * Returns load users by account id for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param accountIds the account ids parameter
     * @return the load users by account id result
     */
    private Map<UUID, Map<String, Object>> loadUsersByAccountId(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        try {
            var resp = userServiceFeignClient.getUsersByAccountIds(new AccountIdsDto(new HashSet<>(accountIds)));
            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || !(resp.getBody() instanceof Map<?, ?> api)) {
                return Map.of();
            }
            Object data = api.get("data");
            if (!(data instanceof List<?> users)) {
                return Map.of();
            }
            Map<UUID, Map<String, Object>> result = new HashMap<>();
            for (Object item : users) {
                if (item instanceof Map<?, ?> rawUser) {
                    Object idValue = rawUser.get("id");
                    if (idValue == null) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(idValue.toString());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> user = (Map<String, Object>) rawUser;
                        result.put(id, user);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * Returns load favorite ids for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param files the files parameter
     * @return the matching result collection
     */
    private Set<UUID> loadFavoriteIds(UUID userId, Collection<StoredFile> files) {
        if (userId == null || files == null || files.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = files.stream()
                .map(StoredFile::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findByUserIdAndTargetIdIn(userId, ids).stream()
                .map(f -> f.getTargetId())
                .collect(Collectors.toSet());
    }

    /**
     * Resolves file information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @return the resolve folder result
     */
    private Folder resolveFolder(UUID folderId) {
        if (folderId == null) return null;
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
    }

    /**
     * Validates file data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param file the file parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
    }

    /** Lọc bỏ file avatar khỏi danh sách document */
    private boolean isAvatarFile(StoredFile f) {
        String path = f.getPath();
        return path != null && (path.startsWith("avatar/employee/") || path.startsWith("avatar/group/"));
    }

    /**
     * Builds file data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param ownerId the owner id parameter
     * @param fileName the file name parameter
     * @return the build object key result
     */
    private String buildObjectKey(UUID folderId, UUID ownerId, String fileName) {
        String folderPart = folderId != null ? String.valueOf(folderId) : "root";
        return "document/owners/" + ownerId + "/" + folderPart + "/" + UUID.randomUUID() + "-" + safeFileName(fileName);
    }

    /**
     * Builds file data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param fileName the file name parameter
     * @return the build chat object key result
     */
    private String buildChatObjectKey(String conversationId, String fileName) {
        return "chat/" + safePathSegment(conversationId) + "/" + UUID.randomUUID() + "-" + safeFileName(fileName);
    }

    /**
     * Builds file data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileName the file name parameter
     * @return the build public object key result
     */
    private String buildPublicObjectKey(String fileName) {
        return "document/public/" + UUID.randomUUID() + "-" + safeFileName(fileName);
    }

    /**
     * Returns safe file name for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileName the file name parameter
     * @return the safe file name result
     */
    private String safeFileName(String fileName) {
        String normalized = fileName == null ? "file" : fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = UNSAFE_FILE_NAME_CHARS.matcher(normalized).replaceAll("_");
        return normalized.isBlank() ? "file" : normalized;
    }

    /**
     * Returns safe path segment for file processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the safe path segment result
     */
    private String safePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return UNSAFE_FILE_NAME_CHARS.matcher(value).replaceAll("_");
    }
}
