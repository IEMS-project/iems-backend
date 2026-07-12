package com.iems.documentservice.service;

import com.iems.documentservice.dto.response.FavoriteItemResponse;
import com.iems.documentservice.entity.Favorite;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoredFileRepository storedFileRepository;
    private final FolderRepository folderRepository;
    private final PermissionHelper permissionHelper;

    /**
     * Creates a new favorite service instance.
     *
     * @param favoriteRepository the favorite repository parameter
     * @param storedFileRepository the stored file repository parameter
     * @param folderRepository the folder repository parameter
     * @param permissionHelper the permission helper parameter
     */
    public FavoriteService(FavoriteRepository favoriteRepository,
                           StoredFileRepository storedFileRepository,
                           FolderRepository folderRepository,
                           PermissionHelper permissionHelper) {
        this.favoriteRepository = favoriteRepository;
        this.storedFileRepository = storedFileRepository;
        this.folderRepository = folderRepository;
        this.permissionHelper = permissionHelper;
    }

    /**
     * Returns toggle for favorite processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param targetId the target id parameter
     * @param type the type parameter
     * @return true if the requested condition is satisfied; otherwise false
     * @throws AppException if a business rule prevents the requested operation
     */
    @Transactional
    public boolean toggle(UUID targetId, String type) {
        UUID userId = permissionHelper.getCurrentUserId();
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("FILE".equals(normalizedType)) {
            var file = storedFileRepository.findById(targetId)
                    .filter(f -> f.getDeletedAt() == null)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
            permissionHelper.enforceReadPermission(file, userId);
        } else if ("FOLDER".equals(normalizedType)) {
            folderRepository.findById(targetId)
                    .filter(f -> f.getDeletedAt() == null)
                    .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
            permissionHelper.enforceFolderReadPermission(targetId, userId);
        } else {
            throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        }
        var existing = favoriteRepository.findByUserIdAndTargetId(userId, targetId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }
        favoriteRepository.save(Favorite.builder()
                .userId(userId)
                .targetId(targetId)
                .createdAt(OffsetDateTime.now())
                .build());
        return true;
    }

    /**
     * Lists favorite information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FavoriteItemResponse> listFavorites() {
        UUID userId = permissionHelper.getCurrentUserId();
        return favoriteRepository.findByUserId(userId).stream()
                .map(fav -> buildResponse(fav))
                .filter(item -> !"UNKNOWN".equals(item.getTargetType()))
                .collect(Collectors.toList());
    }

    /** Kiểm tra nhanh xem item có được user yêu thích không */
    public boolean isFavorite(UUID userId, UUID targetId) {
        return favoriteRepository.findByUserIdAndTargetId(userId, targetId).isPresent();
    }

    /**
     * Builds favorite data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param fav the fav parameter
     * @return the build response result
     */
    private FavoriteItemResponse buildResponse(Favorite fav) {
        var file = storedFileRepository.findById(fav.getTargetId());
        if (file.isPresent()) {
            var f = file.get();
            if (f.getDeletedAt() != null) {
                return unknownFavorite(fav);
            }
            return FavoriteItemResponse.builder()
                    .id(fav.getId())
                    .targetId(fav.getTargetId())
                    .name(f.getName())
                    .targetType("FILE")
                    .size(f.getSize())
                    .path(f.getPath())
                    .mimeType(f.getType())
                    .permission(f.getPermission() != null ? f.getPermission().name() : "PRIVATE")
                    .createdAt(f.getCreatedAt())
                    .ownerId(f.getOwnerId())
                    .parentId(f.getFolder() != null ? f.getFolder().getId() : null)
                    .build();
        }
        var folder = folderRepository.findById(fav.getTargetId());
        if (folder.isPresent()) {
            var fo = folder.get();
            if (fo.getDeletedAt() != null) {
                return unknownFavorite(fav);
            }
            return FavoriteItemResponse.builder()
                    .id(fav.getId())
                    .targetId(fav.getTargetId())
                    .name(fo.getName())
                    .targetType("FOLDER")
                    .permission(fo.getPermission() != null ? fo.getPermission().name() : "PRIVATE")
                    .createdAt(fo.getCreatedAt())
                    .ownerId(fo.getOwnerId())
                    .parentId(fo.getParent() != null ? fo.getParent().getId() : null)
                    .build();
        }
        return unknownFavorite(fav);
    }

    /**
     * Returns unknown favorite for favorite processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fav the fav parameter
     * @return the unknown favorite result
     */
    private FavoriteItemResponse unknownFavorite(Favorite fav) {
        return FavoriteItemResponse.builder()
                .id(fav.getId())
                .targetId(fav.getTargetId())
                .name("?")
                .targetType("UNKNOWN")
                .build();
    }
}
