package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final ShareRepository shareRepository;
    private final FavoriteRepository favoriteRepository;
    private final PermissionHelper permissionHelper;

    public FolderService(FolderRepository folderRepository,
                         ShareRepository shareRepository,
                         FavoriteRepository favoriteRepository,
                         PermissionHelper permissionHelper) {
        this.folderRepository = folderRepository;
        this.shareRepository = shareRepository;
        this.favoriteRepository = favoriteRepository;
        this.permissionHelper = permissionHelper;
    }

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request) {
        UUID userId = permissionHelper.getCurrentUserId();
        permissionHelper.enforceWritePermission(request.getParentId(), userId);
        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        }
        Folder saved = folderRepository.save(Folder.builder()
                .name(request.getName())
                .parent(parent)
                .ownerId(userId)
                .createdAt(OffsetDateTime.now())
                .build());
        return toResponse(saved, userId);
    }

    public List<FolderResponse> listFolders() {
        UUID userId = permissionHelper.getCurrentUserId();
        List<Folder> owned = folderRepository.findByOwnerIdAndDeletedAtIsNull(userId);
        List<UUID> sharedIds = shareRepository.findBySharedWithUserId(userId).stream()
                .filter(s -> "FOLDER".equals(s.getTargetType()))
                .map(Share::getTargetId)
                .collect(Collectors.toList());
        List<Folder> shared = sharedIds.isEmpty() ? List.of() :
                folderRepository.findByIdIn(sharedIds).stream()
                        .filter(f -> f.getDeletedAt() == null)
                        .collect(Collectors.toList());
        List<Folder> publicFolders = folderRepository.findByPermissionAndDeletedAtIsNull(Permission.PUBLIC);
        Set<UUID> seen = new HashSet<>();
        return Stream.concat(Stream.concat(owned.stream(), shared.stream()), publicFolders.stream())
                .filter(f -> seen.add(f.getId()))
                .map(f -> toResponse(f, userId))
                .collect(Collectors.toList());
    }

    public List<FolderResponse> listByParent(UUID parentId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Set<UUID> accessible = new HashSet<>();
        // owned
        folderRepository.findByParentIdAndDeletedAtIsNull(parentId).stream()
                .filter(f -> f.getOwnerId().equals(userId))
                .map(Folder::getId).forEach(accessible::add);
        // shared
        Set<UUID> sharedIds = shareRepository.findBySharedWithUserId(userId).stream()
                .filter(s -> "FOLDER".equals(s.getTargetType()))
                .map(Share::getTargetId).collect(Collectors.toSet());
        folderRepository.findByIdIn(new ArrayList<>(sharedIds)).stream()
                .filter(f -> f.getDeletedAt() == null)
                .filter(f -> Objects.equals(f.getParent() != null ? f.getParent().getId() : null, parentId))
                .map(Folder::getId).forEach(accessible::add);
        // public
        folderRepository.findByParentIdAndDeletedAtIsNull(parentId).stream()
                .filter(f -> f.getPermission() == Permission.PUBLIC)
                .map(Folder::getId).forEach(accessible::add);
        return folderRepository.findByIdIn(new ArrayList<>(accessible)).stream()
                .filter(f -> f.getDeletedAt() == null)
                .map(f -> toResponse(f, userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void rename(UUID folderId, String newName) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        folder.setName(newName);
        folderRepository.save(folder);
    }

    @Transactional
    public void move(UUID folderId, UUID newParentId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        permissionHelper.enforceWritePermission(newParentId, userId);
        if (newParentId != null) {
            Folder newParent = folderRepository.findById(newParentId)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
            // kiểm tra circular reference
            Folder cur = newParent;
            while (cur != null) {
                if (cur.getId().equals(folderId)) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
                cur = cur.getParent();
            }
        }
        folder.setParent(newParentId != null ? folderRepository.findById(newParentId).orElse(null) : null);
        folderRepository.save(folder);
    }

    @Transactional
    public void updatePermission(UUID folderId, Permission permission) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        folder.setPermission(permission);
        folderRepository.save(folder);
    }

    /** Soft-delete folder và toàn bộ sub-folder (file do TrashService xử lý) */
    @Transactional
    public void softDelete(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        softDeleteRecursive(folder);
    }

    /** Dùng bởi TrashService khi cần soft-delete theo cây */
    public void softDeleteRecursive(Folder folder) {
        OffsetDateTime now = OffsetDateTime.now();
        folder.setDeletedAt(now);
        folderRepository.save(folder);
        folderRepository.findByParentIdAndDeletedAtIsNull(folder.getId())
                .forEach(this::softDeleteRecursive);
    }

    public List<com.iems.documentservice.dto.response.SearchResultItem> searchFolders(String query) {
        UUID userId = permissionHelper.getCurrentUserId();
        String q = query == null ? "" : query.toLowerCase();
        return folderRepository.findByOwnerId(userId).stream()
                .filter(f -> f.getName() != null && f.getName().toLowerCase().contains(q))
                .map(f -> com.iems.documentservice.dto.response.SearchResultItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FOLDER")
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public Folder getOrThrow(UUID folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
    }

    public FolderRepository getRepository() {
        return folderRepository;
    }

    public FolderResponse toResponse(Folder folder, UUID userId) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .ownerId(folder.getOwnerId())
                .permission(folder.getPermission())
                .createdAt(folder.getCreatedAt())
                .favorite(favoriteRepository.findByUserIdAndTargetId(userId, folder.getId()).isPresent())
                .build();
    }
}
