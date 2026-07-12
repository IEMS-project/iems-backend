package com.iems.documentservice.service;

import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.AccountIdsDto;
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
    private final UserServiceFeignClient userServiceFeignClient;

    /**
     * Creates a new folder service instance.
     *
     * @param folderRepository the folder repository parameter
     * @param shareRepository the share repository parameter
     * @param favoriteRepository the favorite repository parameter
     * @param permissionHelper the permission helper parameter
     * @param userServiceFeignClient the user service feign client parameter
     */
    public FolderService(FolderRepository folderRepository,
                         ShareRepository shareRepository,
                         FavoriteRepository favoriteRepository,
                         PermissionHelper permissionHelper,
                         UserServiceFeignClient userServiceFeignClient) {
        this.folderRepository = folderRepository;
        this.shareRepository = shareRepository;
        this.favoriteRepository = favoriteRepository;
        this.permissionHelper = permissionHelper;
        this.userServiceFeignClient = userServiceFeignClient;
    }

    /**
     * Creates folder data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the create folder result
     */
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

    /**
     * Lists folder information.
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
        List<Folder> visibleFolders = Stream.concat(Stream.concat(owned.stream(), shared.stream()), publicFolders.stream())
                .filter(f -> seen.add(f.getId()))
                .toList();
        Set<UUID> favoriteIds = loadFavoriteIds(userId, visibleFolders);
        Set<UUID> ownerIds = visibleFolders.stream().map(Folder::getOwnerId).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(ownerIds);

        return visibleFolders.stream()
                .map(f -> toResponse(f, userId, favoriteIds, owners.get(f.getOwnerId())))
                .collect(Collectors.toList());
    }

    /**
     * Lists folder information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param parentId the parent id parameter
     * @return the matching result collection
     */
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
        List<Folder> visibleFolders = folderRepository.findByIdIn(new ArrayList<>(accessible)).stream()
                .filter(f -> f.getDeletedAt() == null)
                .toList();
        Set<UUID> favoriteIds = loadFavoriteIds(userId, visibleFolders);
        Set<UUID> ownerIds = visibleFolders.stream().map(Folder::getOwnerId).collect(Collectors.toSet());
        Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(ownerIds);

        return visibleFolders.stream()
                .map(f -> toResponse(f, userId, favoriteIds, owners.get(f.getOwnerId())))
                .collect(Collectors.toList());
    }

    /**
     * Performs rename for folder processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param newName the new name parameter
     */
    @Transactional
    public void rename(UUID folderId, String newName) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
        folder.setName(newName);
        folderRepository.save(folder);
    }

    /**
     * Performs move for folder processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param newParentId the new parent id parameter
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public void move(UUID folderId, UUID newParentId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
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

    /**
     * Updates folder data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void updatePermission(UUID folderId, Permission permission) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
        folder.setPermission(permission);
        folderRepository.save(folder);
    }

    /**
     * Returns folder for folder processing.
     *
     * @param folderId the folder id parameter
     * @return the folder result
     */
    /** Soft-delete folder và toàn bộ sub-folder (file do TrashService xử lý) */
    @Transactional
    public void softDelete(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
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

    /**
     * Searches folder information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param query the query parameter
     * @return the matching result collection
     */
    public List<com.iems.documentservice.dto.response.SearchResultItem> searchFolders(String query) {
        UUID userId = permissionHelper.getCurrentUserId();
        String q = query == null ? "" : query.trim();
        return folderRepository.findByOwnerIdAndDeletedAtIsNullAndNameContainingIgnoreCase(userId, q).stream()
                .map(f -> com.iems.documentservice.dto.response.SearchResultItem.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FOLDER")
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves folder information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @return the get or throw result
     */
    public Folder getOrThrow(UUID folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
    }

    /**
     * Retrieves folder information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the get repository result
     */
    public FolderRepository getRepository() {
        return folderRepository;
    }

    /**
     * Returns to response for folder processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param folder the folder parameter
     * @param userId the user id parameter
     * @return the to response result
     */
    public FolderResponse toResponse(Folder folder, UUID userId) {
        Map<String, Object> owner = null;
        if (folder.getOwnerId() != null) {
            Map<UUID, Map<String, Object>> owners = loadUsersByAccountId(List.of(folder.getOwnerId()));
            owner = owners.get(folder.getOwnerId());
        }
        return toResponse(folder, userId, loadFavoriteIds(userId, List.of(folder)), owner);
    }

    /**
     * Returns to response for folder processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param folder the folder parameter
     * @param userId the user id parameter
     * @param favoriteIds the favorite ids parameter
     * @param owner the owner parameter
     * @return the to response result
     */
    private FolderResponse toResponse(Folder folder, UUID userId, Set<UUID> favoriteIds, Map<String, Object> owner) {
        var builder = FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .ownerId(folder.getOwnerId())
                .permission(folder.getPermission())
                .createdAt(folder.getCreatedAt())
                .favorite(favoriteIds.contains(folder.getId()));

        if (owner != null) {
            if (owner.get("firstName") != null) {
                builder.ownerName(owner.get("firstName").toString() + " " + (owner.get("lastName") != null ? owner.get("lastName").toString() : ""));
            }
            if (owner.get("email") != null) builder.ownerEmail(owner.get("email").toString());
            if (owner.get("image") != null) builder.ownerAvatar(owner.get("image").toString());
        }

        // Add breadcrumbs
        builder.breadcrumbs(buildFolderBreadcrumbs(folder));

        return builder.build();
    }

    /**
     * Builds folder data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param folder the folder parameter
     * @return the matching result collection
     */
    private List<FolderResponse.BreadcrumbResponse> buildFolderBreadcrumbs(Folder folder) {
        List<FolderResponse.BreadcrumbResponse> crumbs = new ArrayList<>();
        Folder cur = folder;
        while (cur != null) {
            crumbs.add(0, FolderResponse.BreadcrumbResponse.builder()
                    .id(cur.getId())
                    .name(cur.getName())
                    .build());
            cur = cur.getParent();
        }
        return crumbs;
    }

    /**
     * Returns load users by account id for folder processing.
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
     * Returns load favorite ids for folder processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param folders the folders parameter
     * @return the matching result collection
     */
    private Set<UUID> loadFavoriteIds(UUID userId, Collection<Folder> folders) {
        if (userId == null || folders == null || folders.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = folders.stream()
                .map(Folder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findByUserIdAndTargetIdIn(userId, ids).stream()
                .map(f -> f.getTargetId())
                .collect(Collectors.toSet());
    }
}
