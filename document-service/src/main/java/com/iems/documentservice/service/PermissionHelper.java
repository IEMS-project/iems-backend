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

    /** Chỉ owner của file hoặc owner của thư mục cha chứa file mới được thao tác */
    public void enforceFileOwnerOrFolderOwner(StoredFile file, UUID requesterId) {
        if (file.getOwnerId().equals(requesterId)) return;
        Folder parent = file.getFolder();
        while (parent != null) {
            if (parent.getOwnerId().equals(requesterId)) return;
            parent = parent.getParent();
        }
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    /** Chỉ owner của folder hoặc owner của bất kỳ thư mục cha nào mới được thao tác */
    public void enforceFolderOwnerOrParentOwner(Folder folder, UUID requesterId) {
        if (folder.getOwnerId().equals(requesterId)) return;
        Folder parent = folder.getParent();
        while (parent != null) {
            if (parent.getOwnerId().equals(requesterId)) return;
            parent = parent.getParent();
        }
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    /** Kiểm tra quyền đọc file: PUBLIC → ok, SHARED → check share record, PRIVATE → phải là owner (có hỗ trợ thừa kế và quyền chủ thư mục) */
    public void enforceReadPermission(StoredFile file, UUID requesterId) {
        if (file.getPermission() == Permission.PUBLIC) return;
        if (requesterId != null && file.getOwnerId().equals(requesterId)) return;
        if (file.getPermission() == Permission.SHARED && requesterId != null
                && shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(file.getId(), "FILE", requesterId)) {
            return;
        }
        
        // Kiểm tra thừa kế quyền và quyền chủ thư mục cha
        Folder parent = file.getFolder();
        while (parent != null) {
            if (requesterId != null && parent.getOwnerId().equals(requesterId)) return;
            if (parent.getPermission() == Permission.PUBLIC) return;
            if (requesterId != null && shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(parent.getId(), "FOLDER", requesterId)) {
                return;
            }
            parent = parent.getParent();
        }
        
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    /**
     * Kiểm tra quyền ghi vào folder:
     * - null folderId (root) → ok cho mọi user đã xác thực
     * - OWNER → ok
     * - EDITOR share → ok
     * - VIEWER share → lỗi WRITE_PERMISSION_REQUIRED
     * - không có quyền → PERMISSION_DENIED
     * Hỗ trợ thừa kế quyền ghi từ thư mục cha!
     */
    public void enforceWritePermission(UUID folderId, UUID requesterId) {
        if (folderId == null) return;
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        Folder current = folder;
        Boolean hasEditor = null;
        Boolean hasViewer = null;
        
        while (current != null) {
            if (current.getOwnerId().equals(requesterId)) {
                hasEditor = true;
                break;
            }
            if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserIdAndPermission(
                    current.getId(), "FOLDER", requesterId, SharePermission.EDITOR)) {
                hasEditor = true;
                break;
            }
            if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserIdAndPermission(
                    current.getId(), "FOLDER", requesterId, SharePermission.VIEWER)) {
                if (hasEditor == null) {
                    hasViewer = true;
                }
            }
            if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(current.getId(), "FOLDER", requesterId)) {
                if (hasEditor == null && hasViewer == null) {
                    hasViewer = true;
                }
            }
            current = current.getParent();
        }
        
        if (Boolean.TRUE.equals(hasEditor)) return;
        if (Boolean.TRUE.equals(hasViewer)) {
            throw new AppException(DocumentErrorCode.WRITE_PERMISSION_REQUIRED);
        }
        throw new AppException(DocumentErrorCode.PERMISSION_DENIED);
    }

    /** Kiểm tra quyền đọc folder: owner, public, hoặc được share (có hỗ trợ thừa kế từ cha) */
    public void enforceFolderReadPermission(UUID folderId, UUID requesterId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
        
        Folder current = folder;
        while (current != null) {
            if (current.getOwnerId().equals(requesterId)) return;
            if (current.getPermission() == Permission.PUBLIC) return;
            if (shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(current.getId(), "FOLDER", requesterId)) return;
            current = current.getParent();
        }
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
