package com.iems.documentservice.service;

import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.entity.enums.SharePermission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ShareRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import com.iems.documentservice.security.JwtUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PermissionHelper {

    private final FolderRepository folderRepository;
    private final StoredFileRepository storedFileRepository;
    private final ShareRepository shareRepository;

    public PermissionHelper(FolderRepository folderRepository,
                            StoredFileRepository storedFileRepository,
                            ShareRepository shareRepository) {
        this.folderRepository = folderRepository;
        this.storedFileRepository = storedFileRepository;
        this.shareRepository = shareRepository;
    }

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((JwtUserDetails) auth.getPrincipal()).getUserId();
    }

    /** Chỉ owner mới được thao tác */
    public void enforceOwner(UUID ownerId, UUID requesterId) {
        if (!ownerId.equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    /** Kiểm tra quyền đọc file: PUBLIC → ok, SHARED → check share record, PRIVATE → phải là owner */
    public void enforceReadPermission(StoredFile file, UUID requesterId) {
        if (file.getPermission() == Permission.PUBLIC) return;
        if (file.getPermission() == Permission.SHARED && requesterId != null
                && shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(file.getId(), "FILE", requesterId)) {
            return;
        }
        if (!file.getOwnerId().equals(requesterId)) {
            throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
        }
    }

    /**
     * Kiểm tra quyền ghi vào folder:
     * - null folderId (root) → ok cho mọi user đã xác thực
     * - OWNER → ok
     * - EDITOR share → ok
     * - VIEWER share → lỗi WRITE_PERMISSION_REQUIRED
     * - không có quyền → PERMISSION_DENIED
     */
    public void enforceWritePermission(UUID folderId, UUID requesterId) {
        if (folderId == null) return;
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        if (folder.getOwnerId().equals(requesterId)) return;
        if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserIdAndPermission(
                folderId, "FOLDER", requesterId, SharePermission.EDITOR)) return;
        if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", requesterId)) {
            throw new AppException(DocumentErrorCode.WRITE_PERMISSION_REQUIRED);
        }
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    /** Kiểm tra quyền đọc folder: owner, public, hoặc được share */
    public void enforceFolderReadPermission(UUID folderId, UUID requesterId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        if (folder.getOwnerId().equals(requesterId)) return;
        if (folder.getPermission() == Permission.PUBLIC) return;
        if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", requesterId)) return;
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    public boolean validateTargetExists(UUID targetId, String type) {
        if ("FILE".equals(type)) return storedFileRepository.findById(targetId).isPresent();
        if ("FOLDER".equals(type)) return folderRepository.findById(targetId).isPresent();
        return false;
    }

    public boolean validateTargetExistsAndOwned(UUID targetId, String type, UUID ownerId) {
        if ("FILE".equals(type)) {
            return storedFileRepository.findById(targetId)
                    .map(f -> f.getOwnerId().equals(ownerId)).orElse(false);
        }
        if ("FOLDER".equals(type)) {
            return folderRepository.findById(targetId)
                    .map(f -> f.getOwnerId().equals(ownerId)).orElse(false);
        }
        return false;
    }
}
