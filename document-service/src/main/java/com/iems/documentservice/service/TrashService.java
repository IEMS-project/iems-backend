package com.iems.documentservice.service;

import com.iems.documentservice.dto.response.TrashItemResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.exception.DocumentErrorCode;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TrashService {

    private final StoredFileRepository storedFileRepository;
    private final FolderRepository folderRepository;
    private final ObjectStorageService storageService;
    private final PermissionHelper permissionHelper;

    public TrashService(StoredFileRepository storedFileRepository,
                        FolderRepository folderRepository,
                        ObjectStorageService storageService,
                        PermissionHelper permissionHelper) {
        this.storedFileRepository = storedFileRepository;
        this.folderRepository = folderRepository;
        this.storageService = storageService;
        this.permissionHelper = permissionHelper;
    }

    // ──────────────────────────── SOFT DELETE ────────────────────────────

    @Transactional
    public void softDeleteFile(UUID fileId) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getFileOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        file.setDeletedAt(OffsetDateTime.now());
        storedFileRepository.save(file);
    }

    @Transactional
    public void softDeleteFolder(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        softDeleteFolderRecursive(folder);
    }

    private void softDeleteFolderRecursive(Folder folder) {
        OffsetDateTime now = OffsetDateTime.now();
        // Soft-delete tất cả file trong folder
        storedFileRepository.findByFolderIdAndDeletedAtIsNull(folder.getId())
                .forEach(f -> {
                    f.setDeletedAt(now);
                    storedFileRepository.save(f);
                });
        // Đệ quy vào sub-folder
        folderRepository.findByParentIdAndDeletedAtIsNull(folder.getId())
                .forEach(this::softDeleteFolderRecursive);
        // Đánh dấu folder đã xóa
        folder.setDeletedAt(now);
        folderRepository.save(folder);
    }

    // ──────────────────────────── LIST TRASH ────────────────────────────

    public List<TrashItemResponse> listTrash() {
        UUID userId = permissionHelper.getCurrentUserId();
        List<TrashItemResponse> result = new ArrayList<>();

        // Folder đã xóa (chỉ lấy top-level - folder cha không bị xóa)
        folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId).stream()
                .filter(f -> f.getParent() == null || f.getParent().getDeletedAt() == null)
                .forEach(f -> result.add(TrashItemResponse.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FOLDER")
                        .parentId(f.getParent() != null ? f.getParent().getId() : null)
                        .deletedAt(f.getDeletedAt())
                        .createdAt(f.getCreatedAt())
                        .build()));

        // File đã xóa (chỉ lấy file nằm trực tiếp, không nằm trong folder cũng đang bị xóa)
        storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId).stream()
                .filter(f -> f.getFolder() == null || f.getFolder().getDeletedAt() == null)
                .forEach(f -> result.add(TrashItemResponse.builder()
                        .id(f.getId())
                        .name(f.getName())
                        .itemType("FILE")
                        .size(f.getSize())
                        .mimeType(f.getType())
                        .parentId(f.getFolder() != null ? f.getFolder().getId() : null)
                        .deletedAt(f.getDeletedAt())
                        .createdAt(f.getCreatedAt())
                        .build()));

        result.sort((a, b) -> b.getDeletedAt().compareTo(a.getDeletedAt()));
        return result;
    }

    // ──────────────────────────── RESTORE ────────────────────────────

    @Transactional
    public void restoreFile(UUID fileId) {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getFileOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        if (file.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        // Nếu folder cha cũng đang bị xóa, restore folder cha trước
        if (file.getFolder() != null && file.getFolder().getDeletedAt() != null) {
            restoreFolderRecursive(file.getFolder());
        }
        file.setDeletedAt(null);
        storedFileRepository.save(file);
    }

    @Transactional
    public void restoreFolder(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        if (folder.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        // Nếu folder cha cũng đang bị xóa, restore folder cha trước
        if (folder.getParent() != null && folder.getParent().getDeletedAt() != null) {
            restoreFolderRecursive(folder.getParent());
        }
        restoreFolderRecursive(folder);
    }

    private void restoreFolderRecursive(Folder folder) {
        folder.setDeletedAt(null);
        folderRepository.save(folder);
        // Restore các file bên trong
        storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(folder.getOwnerId()).stream()
                .filter(f -> f.getFolder() != null && f.getFolder().getId().equals(folder.getId()))
                .forEach(f -> {
                    f.setDeletedAt(null);
                    storedFileRepository.save(f);
                });
        // Restore sub-folder
        folderRepository.findByOwnerIdAndDeletedAtIsNotNull(folder.getOwnerId()).stream()
                .filter(f -> f.getParent() != null && f.getParent().getId().equals(folder.getId()))
                .forEach(this::restoreFolderRecursive);
    }

    // ──────────────────────────── PERMANENT DELETE ────────────────────────────

    @Transactional
    public void permanentDeleteFile(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getFileOrThrow(fileId);
        permissionHelper.enforceOwner(file.getOwnerId(), userId);
        if (file.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    @Transactional
    public void permanentDeleteFolder(UUID folderId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceOwner(folder.getOwnerId(), userId);
        if (folder.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        permanentDeleteFolderRecursive(folder);
    }

    private void permanentDeleteFolderRecursive(Folder folder) throws Exception {
        // Xóa tất cả file trong folder khỏi storage + DB
        for (StoredFile file : storedFileRepository.findByFolderId(folder.getId())) {
            storageService.delete(file.getPath());
            storedFileRepository.delete(file);
        }
        // Đệ quy vào sub-folder
        for (Folder child : folderRepository.findByParentId(folder.getId())) {
            permanentDeleteFolderRecursive(child);
        }
        folderRepository.delete(folder);
    }

    @Transactional
    public void emptyTrash() throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        // Xóa vĩnh viễn tất cả file trong thùng rác
        for (StoredFile file : storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)) {
            storageService.delete(file.getPath());
            storedFileRepository.delete(file);
        }
        // Xóa vĩnh viễn tất cả folder trong thùng rác
        for (Folder folder : folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)) {
            folderRepository.delete(folder);
        }
    }

    // ──────────────────────────── HELPERS ────────────────────────────

    private StoredFile getFileOrThrow(UUID fileId) {
        return storedFileRepository.findById(fileId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FILE_NOT_FOUND));
    }

    private Folder getFolderOrThrow(UUID folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new AppException(DocumentErrorCode.FOLDER_NOT_FOUND));
    }
}
