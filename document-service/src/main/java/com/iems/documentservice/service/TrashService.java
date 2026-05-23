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
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        file.setDeletedAt(OffsetDateTime.now());
        storedFileRepository.save(file);
    }

    @Transactional
    public void softDeleteFolder(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
        OffsetDateTime now = OffsetDateTime.now();
        softDeleteFolderRecursive(folder, now);
    }

    private void softDeleteFolderRecursive(Folder folder, OffsetDateTime deletionTime) {
        // Soft-delete tất cả file trong folder chưa bị xóa
        storedFileRepository.findByFolderIdAndDeletedAtIsNull(folder.getId())
                .forEach(f -> {
                    f.setDeletedAt(deletionTime);
                    storedFileRepository.save(f);
                });
        // Đệ quy vào sub-folder chưa bị xóa
        folderRepository.findByParentIdAndDeletedAtIsNull(folder.getId())
                .forEach(sub -> softDeleteFolderRecursive(sub, deletionTime));
        // Đánh dấu folder đã xóa
        folder.setDeletedAt(deletionTime);
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
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        if (file.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        // Nếu folder cha cũng đang bị xóa, restore folder cha trước
        if (file.getFolder() != null && file.getFolder().getDeletedAt() != null) {
            restoreFolderRecursive(file.getFolder(), file.getFolder().getDeletedAt());
        }
        file.setDeletedAt(null);
        storedFileRepository.save(file);
    }

    @Transactional
    public void restoreFolder(UUID folderId) {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
        if (folder.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        
        OffsetDateTime deletionTime = folder.getDeletedAt();
        
        // Nếu folder cha cũng đang bị xóa, restore folder cha trước
        if (folder.getParent() != null && folder.getParent().getDeletedAt() != null) {
            restoreFolderRecursive(folder.getParent(), folder.getParent().getDeletedAt());
        }
        restoreFolderRecursive(folder, deletionTime);
    }

    private void restoreFolderRecursive(Folder folder, OffsetDateTime deletionTime) {
        folder.setDeletedAt(null);
        folderRepository.save(folder);
        
        // Chỉ phục hồi những file bên trong có cùng thời gian xóa (deletionTime) với folder cha
        storedFileRepository.findByFolderId(folder.getId()).stream()
                .filter(f -> deletionTime.equals(f.getDeletedAt()))
                .forEach(f -> {
                    f.setDeletedAt(null);
                    storedFileRepository.save(f);
                });
        
        // Chỉ phục hồi những sub-folder bên trong có cùng thời gian xóa (deletionTime) với folder cha
        folderRepository.findByParentId(folder.getId()).stream()
                .filter(sub -> deletionTime.equals(sub.getDeletedAt()))
                .forEach(sub -> restoreFolderRecursive(sub, deletionTime));
    }

    // ──────────────────────────── PERMANENT DELETE ────────────────────────────

    @Transactional
    public void permanentDeleteFile(UUID fileId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        StoredFile file = getFileOrThrow(fileId);
        permissionHelper.enforceFileOwnerOrFolderOwner(file, userId);
        if (file.getDeletedAt() == null) throw new AppException(DocumentErrorCode.INVALID_REQUEST);
        storageService.delete(file.getPath());
        storedFileRepository.delete(file);
    }

    @Transactional
    public void permanentDeleteFolder(UUID folderId) throws Exception {
        UUID userId = permissionHelper.getCurrentUserId();
        Folder folder = getFolderOrThrow(folderId);
        permissionHelper.enforceFolderOwnerOrParentOwner(folder, userId);
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
