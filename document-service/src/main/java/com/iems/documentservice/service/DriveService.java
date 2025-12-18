package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.UpdatePermissionRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.dto.response.SimpleFileResponse;
import com.iems.documentservice.dto.response.BatchDeleteResponse;
import com.iems.documentservice.dto.response.BatchMoveResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.Favorite;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import com.iems.documentservice.repository.ShareRepository;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.security.JwtUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.response.SharedUserResponse;
import com.iems.documentservice.dto.response.FolderContentsResponse;
import com.iems.documentservice.dto.response.SearchResultItem;
import com.iems.documentservice.dto.response.FavoriteItemResponse;
import com.iems.documentservice.dto.response.TrashItemResponse;
import com.iems.documentservice.entity.enums.SharePermission;

@Service
public class DriveService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024; // 50MB

    private final FolderRepository folderRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectStorageService storageService;
    private final ShareRepository shareRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserServiceFeignClient userServiceFeignClient;

    public DriveService(FolderRepository folderRepository,
                        StoredFileRepository storedFileRepository,
                        ObjectStorageService storageService,
                        ShareRepository shareRepository,
                        FavoriteRepository favoriteRepository,
                        UserServiceFeignClient userServiceFeignClient) {
        this.folderRepository = folderRepository;
        this.storedFileRepository = storedFileRepository;
        this.storageService = storageService;
        this.shareRepository = shareRepository;
        this.favoriteRepository = favoriteRepository;
        this.userServiceFeignClient = userServiceFeignClient;
    }

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request) {
        UUID currentUserId = getCurrentUserId();
        
        // Check write permission for the parent folder
        enforceWritePermission(request.getParentId(), currentUserId);
        
        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        }
        Folder folder = Folder.builder()
                .name(request.getName())
                .parent(parent)
                .ownerId(currentUserId)
                .createdAt(OffsetDateTime.now())
                .build();
        folder = folderRepository.save(folder);
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(parent != null ? parent.getId() : null)
                .ownerId(folder.getOwnerId())
                .createdAt(folder.getCreatedAt())
                .build();
    }

    @Transactional
    public FileResponse uploadFile(UUID folderId, MultipartFile file) throws Exception {
        UUID ownerId = getCurrentUserId();
        
        // Check write permission for the folder
        enforceWritePermission(folderId, ownerId);
        
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        }
        String objectKey = generateObjectKey(folderId, ownerId, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), file.getContentType());
        }
        StoredFile stored = StoredFile.builder()
                .name(file.getOriginalFilename())
                .folder(folder)
                .ownerId(ownerId)
                .path(objectKey)
                .size(file.getSize())
                .type(file.getContentType())
                .permission(Permission.PUBLIC)
                .createdAt(OffsetDateTime.now())
                .build();
        stored = storedFileRepository.save(stored);
        return toResponse(stored, null);
    }

    @Transactional
    public java.util.List<SimpleFileResponse> uploadFilesAndBuildPublicUrls(UUID folderId, MultipartFile[] files) throws Exception {
        if (files == null || files.length == 0) return java.util.List.of();
        java.util.List<SimpleFileResponse> results = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            FileResponse fr = uploadFile(folderId, f);
            String publicUrl = storageService.buildPublicUrl(fr.getPath());
            results.add(SimpleFileResponse.builder()
                    .id(fr.getId().toString())
                    .fileName(fr.getName())
                    .url(publicUrl)
                    .type(fr.getType())
                    .build());
        }
        return results;
    }

    @Transactional
    public java.util.List<SimpleFileResponse> uploadChatFiles(String conversationId, MultipartFile[] files) throws Exception {
        if (files == null || files.length == 0) return java.util.List.of();
        UUID ownerId = getCurrentUserId();
        java.util.List<SimpleFileResponse> results = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_UPLOAD_SIZE) {
                throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            }
            // Enforce per-type size constraints (images ≤5MB, videos ≤20MB, others ≤20MB)
            double sizeMb = file.getSize() / (1024.0 * 1024.0);
            String mime = file.getContentType() == null ? "" : file.getContentType();
            if (mime.startsWith("image") && sizeMb > 5.0) {
                throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            }
            if (!mime.startsWith("image") && sizeMb > 20.0) {
                throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            }
            String objectKey = generateChatObjectKey(conversationId, file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                storageService.upload(objectKey, in, file.getSize(), file.getContentType());
            }
            StoredFile stored = StoredFile.builder()
                    .name(file.getOriginalFilename())
                    .folder(null)
                    .ownerId(ownerId)
                    .path(objectKey)
                    .size(file.getSize())
                    .type(file.getContentType())
                    .permission(Permission.PUBLIC)
                    .createdAt(OffsetDateTime.now())
                    .build();
            storedFileRepository.save(stored);
            String publicUrl = storageService.buildPublicUrl(objectKey);
            results.add(SimpleFileResponse.builder()
                    .id(stored.getId().toString())
                    .fileName(stored.getName())
                    .url(publicUrl)
                    .type(stored.getType())
                    .build());
        }
        return results;
    }

    @Transactional
    public java.util.List<SimpleFileResponse> uploadFilesToPublicFolder(MultipartFile[] files) throws Exception {
        if (files == null || files.length == 0) return java.util.List.of();
        UUID ownerId = getCurrentUserId();
        java.util.List<SimpleFileResponse> results = new java.util.ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_UPLOAD_SIZE) {
                throw new AppException(DocumentErrorCode.INVALID_REQUEST);
            }
            
            // Generate object key in public folder
            String objectKey = generatePublicObjectKey(file.getOriginalFilename());
            
            try (InputStream in = file.getInputStream()) {
                storageService.upload(objectKey, in, file.getSize(), file.getContentType());
            }
            
            StoredFile stored = StoredFile.builder()
                    .name(file.getOriginalFilename())
                    .folder(null)
                    .ownerId(ownerId)
                    .path(objectKey)
                    .size(file.getSize())
                    .type(file.getContentType())
                    .permission(Permission.PUBLIC)
                    .createdAt(OffsetDateTime.now())
                    .build();
            storedFileRepository.save(stored);
            
            String publicUrl = storageService.buildPublicUrl(objectKey);
            results.add(SimpleFileResponse.builder()
                    .id(stored.getId().toString())
                    .fileName(stored.getName())
                    .url(publicUrl)
                    .type(stored.getType())
                    .build());
        }
        return results;
    }

    public FileResponse downloadInfo(UUID fileId) throws Exception {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        enforceReadPermission(file, requesterId);
        String presigned = storageService.presignGetUrl(file.getPath());
        return toResponse(file, presigned);
    }

    public InputStream downloadStream(UUID fileId) throws Exception {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        enforceReadPermission(file, requesterId);
        return storageService.download(file.getPath());
    }

    @Transactional
    public void updatePermission(UUID fileId, UpdatePermissionRequest request) {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        enforceOwner(file, requesterId);
        file.setPermission(request.getPermission());
        storedFileRepository.save(file);
    }

    @Transactional
    public void deleteFile(UUID fileId) throws Exception {
        // Use soft delete instead of permanent delete
        softDeleteFile(fileId);
    }

    @Transactional
    public void deleteFolderRecursive(UUID folderId) throws Exception {
        // Use soft delete instead of permanent delete
        softDeleteFolder(folderId);
    }

    private void enforceOwner(StoredFile file, UUID requesterId) {
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    private void enforceReadPermission(StoredFile file, UUID requesterId) {
        if (file.getPermission() == Permission.PUBLIC) {
            return;
        }
        if (file.getPermission() == Permission.SHARED) {
            if (requesterId != null && shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(file.getId(), "FILE", requesterId)) {
                return;
            }
        }
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    private void enforceWritePermission(UUID folderId, UUID requesterId) {
        if (folderId == null) {
            return; // Root folder - allow creation for authenticated users
        }
        
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        // Owner always has write permission
        if (folder.getOwnerId().equals(requesterId)) {
            return;
        }
        
        // Check if user has EDITOR permission through sharing
        if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserIdAndPermission(
                folderId, "FOLDER", requesterId, SharePermission.EDITOR)) {
            return;
        }
        
        // Check if user has VIEWER permission to provide specific error message
        if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", requesterId)) {
            throw new AppException(DocumentErrorCode.WRITE_PERMISSION_REQUIRED);
        }
        
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    private String generateObjectKey(UUID folderId, UUID ownerId, String fileName) {
        String folderPart = folderId != null ? String.valueOf(folderId) : "root";
        long ts = System.currentTimeMillis();
        return "owners/" + ownerId + "/" + folderPart + "/" + ts + "-" + fileName;
    }

    private String generateChatObjectKey(String conversationId, String fileName) {
        long ts = System.currentTimeMillis();
        return "conversation/" + conversationId + "/" + ts + "-" + fileName;
    }

    private String generatePublicObjectKey(String fileName) {
        long ts = System.currentTimeMillis();
        return "public/" + ts + "-" + fileName;
    }

    private FileResponse toResponse(StoredFile file, String presignedUrl) {
        return toResponse(file, presignedUrl, getCurrentUserId());
    }
    
    private FileResponse toResponse(StoredFile file, String presignedUrl, UUID userId) {
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
                .favorite(isFavorite(userId, file.getId()))
                .build();
    }

    public List<FolderResponse> listFolders() {
        UUID userId = getCurrentUserId();
        
        // Get owned folders (excluding soft deleted)
        List<Folder> ownedFolders = folderRepository.findByOwnerIdAndDeletedAtIsNull(userId);
        
        // Get shared folders
        List<UUID> sharedFolderIds = shareRepository.findBySharedWithUserId(userId).stream()
                .filter(s -> "FOLDER".equals(s.getTargetType()))
                .map(Share::getTargetId)
                .collect(Collectors.toList());
        List<Folder> sharedFolders = sharedFolderIds.isEmpty() ? List.of() : 
                folderRepository.findByIdIn(sharedFolderIds).stream()
                        .filter(f -> f.getDeletedAt() == null)
                        .collect(Collectors.toList());
        
        // Get public folders (excluding soft deleted)
        List<Folder> publicFolders = folderRepository.findByPermissionAndDeletedAtIsNull(Permission.PUBLIC);
        
        // Merge and deduplicate
        Set<UUID> seen = new HashSet<>();
        return Stream.concat(
                Stream.concat(ownedFolders.stream(), sharedFolders.stream()),
                publicFolders.stream()
        ).filter(f -> seen.add(f.getId()))
                .map(f -> FolderResponse.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .ownerId(f.getOwnerId())
                .permission(f.getPermission())
                        .createdAt(f.getCreatedAt())
                .favorite(isFavorite(userId, f.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    public List<FileResponse> listFiles() {
        UUID ownerId = getCurrentUserId();
        return storedFileRepository.findByOwnerIdAndDeletedAtIsNull(ownerId).stream()
                .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
                .map(f -> toResponse(f, null, ownerId))
                .collect(Collectors.toList());
    }

    public List<FileResponse> listFilesInFolder(UUID folderId) {
    UUID requesterId = getCurrentUserId();
    Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));

    // Allow if owner
    if (folder.getOwnerId().equals(requesterId)) {
        return storedFileRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
            .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
            .map(f -> toResponse(f, null, requesterId))
            .collect(Collectors.toList());
    }

    // Allow public folder (but still check individual file permissions)
    if (folder.getPermission() == Permission.PUBLIC) {
        return storedFileRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
            .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
            .filter(f -> {
                try {
                    // Check if user can read this specific file
                    enforceReadPermission(f, requesterId);
                    return true;
                } catch (Exception e) {
                    return false; // Skip files user can't read
                }
            })
            .map(f -> toResponse(f, null, requesterId))
            .collect(Collectors.toList());
    }

    // Allow if folder is shared with requester (but still need to check individual file permissions)
    if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", requesterId)) {
        return storedFileRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
            .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
            .filter(f -> {
                try {
                    // Check if user can read this specific file
                    enforceReadPermission(f, requesterId);
                    return true;
                } catch (Exception e) {
                    return false; // Skip files user can't read
                }
            })
            .map(f -> toResponse(f, null, requesterId))
            .collect(Collectors.toList());
    }

    throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    public List<FolderResponse> listFoldersByParent(UUID parentId) {
        UUID userId = getCurrentUserId();
        
        // Get folders that user has access to
        Set<UUID> accessibleFolderIds = new HashSet<>();
        
        // Find owned folders (excluding soft deleted)
        List<Folder> ownedFolders = folderRepository.findByParentIdAndDeletedAtIsNull(parentId).stream()
                .filter(f -> f.getOwnerId().equals(userId))
                .collect(Collectors.toList());
        accessibleFolderIds.addAll(ownedFolders.stream().map(Folder::getId).collect(Collectors.toSet()));
        
        // Find shared folders (where user has shared access)
    List<Share> sharedAccess = shareRepository.findBySharedWithUserId(userId).stream()
        .filter(s -> "FOLDER".equals(s.getTargetType()))
        .collect(Collectors.toList());
    Set<UUID> sharedFolderIds = sharedAccess.stream()
        .map(Share::getTargetId)
        .collect(Collectors.toSet());
        
        // Only include folders that are in the same parent folder and not deleted
        List<UUID> sharedFolderIdsInParent = folderRepository.findByIdIn(new ArrayList<>(sharedFolderIds)).stream()
                .filter(f -> f.getDeletedAt() == null)
                .filter(f -> f.getParent() != null ? Objects.equals(f.getParent().getId(), parentId) : parentId == null)
                .map(Folder::getId)
                .collect(Collectors.toList());
        accessibleFolderIds.addAll(sharedFolderIdsInParent);
        
        // Find public folders (excluding soft deleted)
        List<UUID> publicFolderIds = folderRepository.findByParentIdAndDeletedAtIsNull(parentId).stream()
                .filter(f -> f.getPermission() == Permission.PUBLIC)
                .map(Folder::getId)
                .collect(Collectors.toList());
        accessibleFolderIds.addAll(publicFolderIds);
        
        // Return all accessible folders (excluding soft deleted)
        return folderRepository.findByIdIn(new ArrayList<>(accessibleFolderIds)).stream()
                .filter(f -> f.getDeletedAt() == null)
                .map(f -> FolderResponse.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .ownerId(f.getOwnerId())
                        .permission(f.getPermission())
                        .createdAt(f.getCreatedAt())
                        .favorite(isFavorite(userId, f.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    public FolderContentsResponse listFolderContents(UUID folderId) {
        UUID requesterId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));

        // Owner allowed
        if (!folder.getOwnerId().equals(requesterId)) {
            // Not owner: allow if public or shared
            if (folder.getPermission() != Permission.PUBLIC && !shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", requesterId)) {
                throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
            }
        }

        List<FolderResponse> folders = listFoldersByParent(folderId);
        List<FileResponse> files = listFilesInFolder(folderId);
        
        return com.iems.documentservice.dto.response.FolderContentsResponse.builder()
                .folders(folders)
                .files(files)
                .build();
    }


    public List<FileResponse> listAccessibleFiles() {
        UUID requesterId = getCurrentUserId();
        // owned
        List<StoredFile> owned = storedFileRepository.findByOwnerId(requesterId);
        // public
        List<StoredFile> publicFiles = storedFileRepository.findByPermission(Permission.PUBLIC).stream()
                .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
                .collect(Collectors.toList());
        // shared
        List<Share> shares = shareRepository.findBySharedWithUserId(requesterId);
        Set<UUID> sharedFileIds = shares.stream()
                .filter(s -> "FILE".equals(s.getTargetType()))
                .map(Share::getTargetId)
                .collect(Collectors.toSet());
        List<StoredFile> sharedFiles = sharedFileIds.isEmpty() ? List.of() : storedFileRepository.findByIdIn(sharedFileIds);

        // merge unique by id
        Set<UUID> seen = new HashSet<>();
        return List.of(owned, publicFiles, sharedFiles).stream()
                .flatMap(List::stream)
                .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
                .filter(f -> seen.add(f.getId()))
                .map(f -> toResponse(f, null, requesterId))
                .collect(Collectors.toList());
    }

    // Search across folders and files by name (owner only)
    public List<SearchResultItem> search(String query) {
        String q = query == null ? "" : query.toLowerCase();
        UUID ownerId = getCurrentUserId();
        var folders = folderRepository.findByOwnerId(ownerId).stream()
                .filter(f -> f.getName().toLowerCase().contains(q))
                .map(f -> com.iems.documentservice.dto.response.SearchResultItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FOLDER")
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .createdAt(f.getCreatedAt())
                        .build());
        var files = storedFileRepository.findByOwnerId(ownerId).stream()
                .filter(f -> f.getPath() == null || !(f.getPath().startsWith("employee_avatar/") || f.getPath().startsWith("group_avatar/")))
                .filter(f -> f.getName().toLowerCase().contains(q))
                .map(f -> com.iems.documentservice.dto.response.SearchResultItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FILE")
                        .parentId(f.getFolder() != null ? f.getFolder().getId() : null)
                        .size(f.getSize())
                        .mimeType(f.getType())
                        .createdAt(f.getCreatedAt())
                        .build());
        return java.util.stream.Stream.concat(folders, files).collect(Collectors.toList());
    }

    // Favorites
    @Transactional
    public boolean toggleFavorite(UUID targetId, String type) {
        UUID userId = getCurrentUserId();
        
        // Validate type and check if target exists
        if (!validateTargetExists(targetId, type)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
        }
        
        var existing = favoriteRepository.findByUserIdAndTargetId(userId, targetId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }
        Favorite fav = Favorite.builder()
                .userId(userId)
                .targetId(targetId)
                .createdAt(OffsetDateTime.now())
                .build();
        favoriteRepository.save(fav);
        return true;
    }

    public List<FavoriteItemResponse> listFavorites() {
        UUID userId = getCurrentUserId();
        return favoriteRepository.findByUserId(userId).stream()
                .map(f -> {
                    // Check file first (as requested)
                    var file = storedFileRepository.findById(f.getTargetId());
                    if (file.isPresent()) {
                        var fileEntity = file.get();
                        return com.iems.documentservice.dto.response.FavoriteItemResponse.builder()
                                .id(f.getId())
                                .targetId(f.getTargetId())
                                .name(fileEntity.getName())
                                .targetType("FILE")
                                .size(fileEntity.getSize())
                                .path(fileEntity.getPath())
                                .mimeType(fileEntity.getType())
                                .permission(fileEntity.getPermission() != null ? fileEntity.getPermission().name() : "PRIVATE")
                                .createdAt(fileEntity.getCreatedAt())
                                .ownerId(fileEntity.getOwnerId())
                                .parentId(fileEntity.getFolder() != null ? fileEntity.getFolder().getId() : null)
                                .build();
                    }
                    
                    // Check folder
                    var folder = folderRepository.findById(f.getTargetId());
                    if (folder.isPresent()) {
                        var folderEntity = folder.get();
                        return com.iems.documentservice.dto.response.FavoriteItemResponse.builder()
                                .id(f.getId())
                                .targetId(f.getTargetId())
                                .name(folderEntity.getName())
                                .targetType("FOLDER")
                                .permission(folderEntity.getPermission() != null ? folderEntity.getPermission().name() : "PRIVATE")
                                .createdAt(folderEntity.getCreatedAt())
                                .ownerId(folderEntity.getOwnerId())
                                .parentId(folderEntity.getParent() != null ? folderEntity.getParent().getId() : null)
                                .build();
                    }
                    
                    // Unknown/deleted item
                    return com.iems.documentservice.dto.response.FavoriteItemResponse.builder()
                            .id(f.getId())
                            .targetId(f.getTargetId())
                            .name("?")
                            .targetType("UNKNOWN")
                            .build();
                })
                .filter(item -> !"UNKNOWN".equals(item.getTargetType())) // Filter out deleted items
                .collect(Collectors.toList());
    }

    // Unified share/unshare for both folders and files
    @Transactional
    public void shareItem(UUID itemId, String type, ShareRequest request) {
        UUID ownerId = getCurrentUserId();
        
        // Validate type and check if target exists and user owns it
        if (!validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
        }
        
        for (UUID uid : request.getUserIds()) {
            if (!shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(itemId, type, uid)) {
                Share share = Share.builder()
                        .targetId(itemId)
                        .targetType(type)
                        .sharedWithUserId(uid)
                        .permission(request.getPermission())
                        .createdAt(OffsetDateTime.now())
                        .build();
                shareRepository.save(share);
            }
        }
        
        // For files, ensure permission is SHARED
        if ("FILE".equals(type)) {
            var file = storedFileRepository.findById(itemId);
            if (file.isPresent() && file.get().getPermission() == Permission.PRIVATE) {
                file.get().setPermission(Permission.SHARED);
                storedFileRepository.save(file.get());
            }
        }
    }

    @Transactional
    public void unshareItem(UUID itemId, String type, ShareRequest request) {
        UUID ownerId = getCurrentUserId();
        
        // Validate type and check if target exists and user owns it
        if (!validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
        }
        
        for (UUID uid : request.getUserIds()) {
            shareRepository.deleteByTargetIdAndTargetTypeAndSharedWithUserId(itemId, type, uid);
        }
    }

    private boolean validateTargetExists(UUID targetId, String type) {
        if ("FILE".equals(type)) {
            return storedFileRepository.findById(targetId).isPresent();
        } else if ("FOLDER".equals(type)) {
            return folderRepository.findById(targetId).isPresent();
        }
        return false;
    }
    
    private boolean validateTargetExistsAndOwned(UUID targetId, String type, UUID ownerId) {
        if ("FILE".equals(type)) {
            var file = storedFileRepository.findById(targetId);
            return file.isPresent() && file.get().getOwnerId().equals(ownerId);
        } else if ("FOLDER".equals(type)) {
            var folder = folderRepository.findById(targetId);
            return folder.isPresent() && folder.get().getOwnerId().equals(ownerId);
        }
        return false;
    }
    
    private boolean isFavorite(UUID userId, UUID targetId) {
        return favoriteRepository.findByUserIdAndTargetId(userId, targetId).isPresent();
    }

    // Update folder permission
    @Transactional
    public void updateFolderPermission(UUID folderId, Permission permission) {
        UUID ownerId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        folder.setPermission(permission);
        folderRepository.save(folder);
    }

    // Update file permission
    @Transactional
    public void updateFilePermission(UUID fileId, Permission permission) {
        UUID ownerId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        
        if (!file.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        file.setPermission(permission);
        storedFileRepository.save(file);
    }

    // Rename folder
    @Transactional
    public void renameFolder(UUID folderId, String newName) {
        UUID ownerId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        folder.setName(newName);
        folderRepository.save(folder);
    }

    // Rename file
    @Transactional
    public void renameFile(UUID fileId, String newName) {
        UUID ownerId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        
        if (!file.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        file.setName(newName);
        storedFileRepository.save(file);
    }


    // Get shared users for an item
    public List<SharedUserResponse> getSharedUsers(UUID itemId, String type) {
        UUID ownerId = getCurrentUserId();
        
        // Validate ownership
        if (!validateTargetExistsAndOwned(itemId, type, ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        List<Share> shares = shareRepository.findByTargetIdAndTargetType(itemId, type);

        return shares.stream().map(share -> {
            var builder = com.iems.documentservice.dto.response.SharedUserResponse.builder()
                    .shareId(share.getId())
                    .userId(share.getSharedWithUserId())
                    .permission(share.getPermission())
                    .sharedAt(share.getCreatedAt());

            try {
                var resp = userServiceFeignClient.getUserById(share.getSharedWithUserId());
                if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    // Feign returns a Map<String,Object> for the ApiResponseDto wrapper
                    Object bodyObj = resp.getBody();
                    if (bodyObj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> apiDto = (java.util.Map<String, Object>) bodyObj;
                        Object dataObj = apiDto.get("data");
                        if (dataObj instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> userMap = (java.util.Map<String, Object>) dataObj;
                            if (userMap.containsKey("firstName") && userMap.get("firstName") != null)
                                builder.firstName(userMap.get("firstName").toString());
                            if (userMap.containsKey("lastName") && userMap.get("lastName") != null)
                                builder.lastName(userMap.get("lastName").toString());
                            if (userMap.containsKey("email") && userMap.get("email") != null)
                                builder.email(userMap.get("email").toString());
                            if (userMap.containsKey("image") && userMap.get("image") != null)
                                builder.image(userMap.get("image").toString());
                        }
                    }
                }
            } catch (Exception ex) {
                // ignore failures to fetch user details - fall back to ids only
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    // Update share permission
    @Transactional
    public void updateSharePermission(UUID shareId, SharePermission permission) {
        UUID ownerId = getCurrentUserId();
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        // Validate ownership of the shared item
        if (!validateTargetExistsAndOwned(share.getTargetId(), share.getTargetType(), ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        share.setPermission(permission);
        shareRepository.save(share);
    }

    // Remove share
    @Transactional
    public void removeShare(UUID shareId) {
        UUID ownerId = getCurrentUserId();
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        // Validate ownership of the shared item
        if (!validateTargetExistsAndOwned(share.getTargetId(), share.getTargetType(), ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        shareRepository.delete(share);
    }

    // Move folder to new parent
    @Transactional
    public void moveFolder(UUID folderId, UUID newParentId) {
        UUID ownerId = getCurrentUserId();
        
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        // Check ownership of the folder being moved
        if (!folder.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        // Check permission to move to destination
        enforceWritePermission(newParentId, ownerId);
        
        // Check for circular reference
        if (newParentId != null) {
            Folder newParent = folderRepository.findById(newParentId)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
            
            // Check if moving folder would create circular reference
            Folder current = newParent;
            while (current != null) {
                if (current.getId().equals(folderId)) {
                    throw new AppException(DocumentErrorCode.INVALID_REQUEST);
                }
                current = current.getParent();
            }
        }
        
        folder.setParent(newParentId != null ? folderRepository.findById(newParentId).orElse(null) : null);
        folderRepository.save(folder);
    }

    // Move file to new folder
    @Transactional
    public void moveFile(UUID fileId, UUID newFolderId) {
        UUID ownerId = getCurrentUserId();
        
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        
        // Check ownership of the file being moved
        if (!file.getOwnerId().equals(ownerId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        // Check permission to move to destination folder
        enforceWritePermission(newFolderId, ownerId);
        
        file.setFolder(newFolderId != null ? folderRepository.findById(newFolderId).orElse(null) : null);
        storedFileRepository.save(file);
    }

    @Transactional
    public BatchDeleteResponse batchDelete(List<UUID> fileIds, List<UUID> folderIds) throws Exception {
        UUID requesterId = getCurrentUserId();
        
        List<UUID> successfulFileIds = new ArrayList<>();
        List<UUID> successfulFolderIds = new ArrayList<>();
        List<BatchDeleteResponse.FailedItem> failedItems = new ArrayList<>();
        
        int totalRequested = 0;
        if (fileIds != null) totalRequested += fileIds.size();
        if (folderIds != null) totalRequested += folderIds.size();
        
        // Delete files
        if (fileIds != null && !fileIds.isEmpty()) {
            for (UUID fileId : fileIds) {
                try {
                    StoredFile file = storedFileRepository.findById(fileId)
                            .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
                    enforceOwner(file, requesterId);
                    storageService.delete(file.getPath());
                    storedFileRepository.delete(file);
                    successfulFileIds.add(fileId);
                } catch (Exception e) {
                    // Log error but continue with other files
                    System.err.println("Error deleting file " + fileId + ": " + e.getMessage());
                    failedItems.add(BatchDeleteResponse.FailedItem.builder()
                            .id(fileId)
                            .type("file")
                            .reason(e.getMessage())
                            .build());
                }
            }
        }
        
        // Delete folders
        if (folderIds != null && !folderIds.isEmpty()) {
            for (UUID folderId : folderIds) {
                try {
                    deleteFolderRecursive(folderId);
                    successfulFolderIds.add(folderId);
                } catch (Exception e) {
                    // Log error but continue with other folders
                    System.err.println("Error deleting folder " + folderId + ": " + e.getMessage());
                    failedItems.add(BatchDeleteResponse.FailedItem.builder()
                            .id(folderId)
                            .type("folder")
                            .reason(e.getMessage())
                            .build());
                }
            }
        }
        
        int successCount = successfulFileIds.size() + successfulFolderIds.size();
        int failureCount = failedItems.size();
        
        return BatchDeleteResponse.builder()
                .totalRequested(totalRequested)
                .successCount(successCount)
                .failureCount(failureCount)
                .successfulFileIds(successfulFileIds)
                .successfulFolderIds(successfulFolderIds)
                .failedItems(failedItems)
                .build();
    }

    @Transactional
    public BatchMoveResponse batchMove(List<UUID> fileIds, List<UUID> folderIds, UUID destinationFolderId) {
        UUID requesterId = getCurrentUserId();
        
        List<UUID> successfulFileIds = new ArrayList<>();
        List<UUID> successfulFolderIds = new ArrayList<>();
        List<BatchMoveResponse.FailedItem> failedItems = new ArrayList<>();
        
        int totalRequested = 0;
        if (fileIds != null) totalRequested += fileIds.size();
        if (folderIds != null) totalRequested += folderIds.size();
        
        // Move files
        if (fileIds != null && !fileIds.isEmpty()) {
            for (UUID fileId : fileIds) {
                try {
                    StoredFile file = storedFileRepository.findById(fileId)
                            .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
                    
                    // Check ownership
                    if (!file.getOwnerId().equals(requesterId)) {
                        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
                    }
                    
                    // Check permission to move to destination
                    enforceWritePermission(destinationFolderId, requesterId);
                    
                    file.setFolder(destinationFolderId != null ? folderRepository.findById(destinationFolderId).orElse(null) : null);
                    storedFileRepository.save(file);
                    successfulFileIds.add(fileId);
                } catch (Exception e) {
                    System.err.println("Error moving file " + fileId + ": " + e.getMessage());
                    failedItems.add(BatchMoveResponse.FailedItem.builder()
                            .id(fileId)
                            .type("file")
                            .reason(e.getMessage())
                            .build());
                }
            }
        }
        
        // Move folders
        if (folderIds != null && !folderIds.isEmpty()) {
            for (UUID folderId : folderIds) {
                try {
                    Folder folder = folderRepository.findById(folderId)
                            .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
                    
                    // Check ownership
                    if (!folder.getOwnerId().equals(requesterId)) {
                        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
                    }
                    
                    // Check permission to move to destination
                    enforceWritePermission(destinationFolderId, requesterId);
                    
                    // Check for circular reference
                    if (destinationFolderId != null) {
                        Folder newParent = folderRepository.findById(destinationFolderId)
                                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
                        
                        Folder current = newParent;
                        while (current != null) {
                            if (current.getId().equals(folderId)) {
                                throw new AppException(DocumentErrorCode.INVALID_REQUEST);
                            }
                            current = current.getParent();
                        }
                    }
                    
                    folder.setParent(destinationFolderId != null ? folderRepository.findById(destinationFolderId).orElse(null) : null);
                    folderRepository.save(folder);
                    successfulFolderIds.add(folderId);
                } catch (Exception e) {
                    System.err.println("Error moving folder " + folderId + ": " + e.getMessage());
                    failedItems.add(BatchMoveResponse.FailedItem.builder()
                            .id(folderId)
                            .type("folder")
                            .reason(e.getMessage())
                            .build());
                }
            }
        }
        
        int successCount = successfulFileIds.size() + successfulFolderIds.size();
        int failureCount = failedItems.size();
        
        return BatchMoveResponse.builder()
                .totalRequested(totalRequested)
                .successCount(successCount)
                .failureCount(failureCount)
                .successfulFileIds(successfulFileIds)
                .successfulFolderIds(successfulFolderIds)
                .failedItems(failedItems)
                .build();
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    // ==================== TRASH / SOFT DELETE METHODS ====================

    /**
     * Soft delete a file - moves it to trash instead of permanently deleting
     */
    @Transactional
    public void softDeleteFile(UUID fileId) {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        enforceOwner(file, requesterId);
        
        file.setDeletedAt(OffsetDateTime.now());
        storedFileRepository.save(file);
    }

    /**
     * Soft delete a folder and all its contents recursively
     */
    @Transactional
    public void softDeleteFolder(UUID folderId) {
        UUID requesterId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        if (!folder.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        softDeleteFolderRecursive(folder);
    }

    private void softDeleteFolderRecursive(Folder folder) {
        OffsetDateTime now = OffsetDateTime.now();
        
        // Soft delete all files in this folder
        List<StoredFile> files = storedFileRepository.findByFolderIdAndDeletedAtIsNull(folder.getId());
        for (StoredFile file : files) {
            file.setDeletedAt(now);
            storedFileRepository.save(file);
        }
        
        // Recurse into children folders
        List<Folder> children = folderRepository.findByParentIdAndDeletedAtIsNull(folder.getId());
        for (Folder child : children) {
            softDeleteFolderRecursive(child);
        }
        
        // Mark folder as deleted
        folder.setDeletedAt(now);
        folderRepository.save(folder);
    }

    /**
     * List all trash items (soft deleted files and folders) for current user
     */
    public List<TrashItemResponse> listTrash() {
        UUID userId = getCurrentUserId();
        List<TrashItemResponse> result = new java.util.ArrayList<>();
        
        // Get deleted folders
        List<Folder> deletedFolders = folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);
        for (Folder folder : deletedFolders) {
            // Only show top-level deleted folders (not children of deleted folders)
            if (folder.getParent() == null || folder.getParent().getDeletedAt() == null) {
                result.add(TrashItemResponse.builder()
                        .id(folder.getId())
                        .name(folder.getName())
                        .itemType("FOLDER")
                        .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                        .deletedAt(folder.getDeletedAt())
                        .createdAt(folder.getCreatedAt())
                        .build());
            }
        }
        
        // Get deleted files
        List<StoredFile> deletedFiles = storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);
        for (StoredFile file : deletedFiles) {
            // Only show files that were directly deleted (not inside a deleted folder)
            if (file.getFolder() == null || file.getFolder().getDeletedAt() == null) {
                result.add(TrashItemResponse.builder()
                        .id(file.getId())
                        .name(file.getName())
                        .itemType("FILE")
                        .size(file.getSize())
                        .mimeType(file.getType())
                        .parentId(file.getFolder() != null ? file.getFolder().getId() : null)
                        .deletedAt(file.getDeletedAt())
                        .createdAt(file.getCreatedAt())
                        .build());
            }
        }
        
        // Sort by deletedAt descending (most recent first)
        result.sort((a, b) -> b.getDeletedAt().compareTo(a.getDeletedAt()));
        
        return result;
    }

    /**
     * Restore a file from trash to its original location
     */
    @Transactional
    public void restoreFile(UUID fileId) {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        if (file.getDeletedAt() == null) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        
        // Check if original folder still exists and is not deleted
        if (file.getFolder() != null && file.getFolder().getDeletedAt() != null) {
            // Restore parent folder first
            restoreFolderRecursive(file.getFolder());
        }
        
        file.setDeletedAt(null);
        storedFileRepository.save(file);
    }

    /**
     * Restore a folder and all its contents from trash to original location
     */
    @Transactional
    public void restoreFolder(UUID folderId) {
        UUID requesterId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        if (!folder.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        if (folder.getDeletedAt() == null) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        
        // If parent folder is also deleted, restore it first
        if (folder.getParent() != null && folder.getParent().getDeletedAt() != null) {
            restoreFolderRecursive(folder.getParent());
        }
        
        restoreFolderRecursive(folder);
    }

    private void restoreFolderRecursive(Folder folder) {
        // Restore this folder
        folder.setDeletedAt(null);
        folderRepository.save(folder);
        
        // Restore all files in this folder that were deleted at the same time or after
        List<StoredFile> files = storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(folder.getOwnerId());
        for (StoredFile file : files) {
            if (file.getFolder() != null && file.getFolder().getId().equals(folder.getId())) {
                file.setDeletedAt(null);
                storedFileRepository.save(file);
            }
        }
        
        // Restore child folders that were deleted
        List<Folder> deletedChildren = folderRepository.findByOwnerIdAndDeletedAtIsNotNull(folder.getOwnerId());
        for (Folder child : deletedChildren) {
            if (child.getParent() != null && child.getParent().getId().equals(folder.getId())) {
                restoreFolderRecursive(child);
            }
        }
    }

    /**
     * Permanently delete a file from trash
     */
    @Transactional
    public void permanentDeleteFile(UUID fileId) throws Exception {
        UUID requesterId = getCurrentUserId();
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
        
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        if (file.getDeletedAt() == null) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    /**
     * Permanently delete a folder and all contents from trash
     */
    @Transactional
    public void permanentDeleteFolder(UUID folderId) throws Exception {
        UUID requesterId = getCurrentUserId();
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        if (!folder.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
        
        if (folder.getDeletedAt() == null) {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        
        permanentDeleteFolderRecursive(folder);
    }

    private void permanentDeleteFolderRecursive(Folder folder) throws Exception {
        // Delete all files in this folder
        List<StoredFile> files = storedFileRepository.findByFolderId(folder.getId());
        for (StoredFile file : files) {
            storageService.delete(file.getPath());
            storedFileRepository.delete(file);
        }
        
        // Recurse into children
        List<Folder> children = folderRepository.findByParentId(folder.getId());
        for (Folder child : children) {
            permanentDeleteFolderRecursive(child);
        }
        
        folderRepository.delete(folder);
    }

    /**
     * Empty the entire trash - permanently delete all items
     */
    @Transactional
    public void emptyTrash() throws Exception {
        UUID userId = getCurrentUserId();
        
        // Delete all trashed files
        List<StoredFile> deletedFiles = storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);
        for (StoredFile file : deletedFiles) {
            storageService.delete(file.getPath());
            storedFileRepository.delete(file);
        }
        
        // Delete all trashed folders
        List<Folder> deletedFolders = folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);
        for (Folder folder : deletedFolders) {
            folderRepository.delete(folder);
        }
    }
}


