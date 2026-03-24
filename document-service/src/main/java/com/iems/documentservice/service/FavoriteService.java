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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final StoredFileRepository storedFileRepository;
    private final FolderRepository folderRepository;
    private final PermissionHelper permissionHelper;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           StoredFileRepository storedFileRepository,
                           FolderRepository folderRepository,
                           PermissionHelper permissionHelper) {
        this.favoriteRepository = favoriteRepository;
        this.storedFileRepository = storedFileRepository;
        this.folderRepository = folderRepository;
        this.permissionHelper = permissionHelper;
    }

    @Transactional
    public boolean toggle(UUID targetId, String type) {
        UUID userId = permissionHelper.getCurrentUserId();
        if (!permissionHelper.validateTargetExists(targetId, type)) {
            throw new AppException(DocumentErrorCode.FOLDER_NOT_FOUND);
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

    private FavoriteItemResponse buildResponse(Favorite fav) {
        var file = storedFileRepository.findById(fav.getTargetId());
        if (file.isPresent()) {
            var f = file.get();
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
        return FavoriteItemResponse.builder()
                .id(fav.getId())
                .targetId(fav.getTargetId())
                .name("?")
                .targetType("UNKNOWN")
                .build();
    }
}
