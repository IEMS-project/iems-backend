package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.response.*;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.entity.enums.SharePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Facade điều phối toàn bộ domain Drive.
 * Controller chỉ tương tác với class này; logic thực tế nằm ở các service con.
 */
@Service
public class DriveService {

    private final FileService fileService;
    private final FolderService folderService;
    private final ShareService shareService;
    private final FavoriteService favoriteService;
    private final TrashService trashService;
    private final PermissionHelper permissionHelper;
    private final ActivityService activityService;

    public DriveService(FileService fileService,
                        FolderService folderService,
                        ShareService shareService,
                        FavoriteService favoriteService,
                        TrashService trashService,
                        PermissionHelper permissionHelper,
                        ActivityService activityService) {
        this.fileService = fileService;
        this.folderService = folderService;
        this.shareService = shareService;
        this.favoriteService = favoriteService;
        this.trashService = trashService;
        this.permissionHelper = permissionHelper;
        this.activityService = activityService;
    }

    // ══════════════════════════ FOLDER ══════════════════════════

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request) {
        FolderResponse response = folderService.createFolder(request);
        activityService.log("FOLDER", response.getId(), "documents.activity.item.created", payload("itemName", response.getName()));
        return response;
    }

    public List<FolderResponse> listFolders() {
        return folderService.listFolders();
    }

    public FolderContentsResponse listFolderContents(UUID folderId) {
        // fileService.listFilesInFolder đã kiểm tra quyền truy cập folder nội bộ
        List<FolderResponse> folders = folderService.listByParent(folderId);
        List<FileResponse> files = fileService.listFilesInFolder(folderId);
        return FolderContentsResponse.builder().folders(folders).files(files).build();
    }

    @Transactional
    public void updateFolderPermission(UUID folderId, Permission permission) {
        folderService.updatePermission(folderId, permission);
        activityService.log("FOLDER", folderId, "documents.activity.item.permissionUpdated",
            payload("permission", permission.name()));
    }

    @Transactional
    public void renameFolder(UUID folderId, String newName) {
        folderService.rename(folderId, newName);
        activityService.log("FOLDER", folderId, "documents.activity.item.renamed", payload("newName", newName));
    }

    @Transactional
    public void moveFolder(UUID folderId, UUID newParentId) {
        folderService.move(folderId, newParentId);
        activityService.log("FOLDER", folderId, "documents.activity.item.moved");
    }

    @Transactional
    public void deleteFolderRecursive(UUID folderId) {
        trashService.softDeleteFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.movedToTrash");
    }

    // ══════════════════════════ FILE ══════════════════════════

    @Transactional
    public FileResponse uploadFile(UUID folderId, MultipartFile file) throws Exception {
        FileResponse response = fileService.uploadFile(folderId, file);
        activityService.log("FILE", response.getId(), "documents.activity.item.created", payload("itemName", response.getName()));
        return response;
    }

    @Transactional
    public List<SimpleFileResponse> uploadFilesAndBuildPublicUrls(UUID folderId, MultipartFile[] files) throws Exception {
        return fileService.uploadBatch(folderId, files);
    }

    @Transactional
    public List<SimpleFileResponse> uploadChatFiles(String conversationId, MultipartFile[] files) throws Exception {
        return fileService.uploadChatFiles(conversationId, files);
    }

    @Transactional
    public List<SimpleFileResponse> uploadFilesToPublicFolder(MultipartFile[] files) throws Exception {
        return fileService.uploadPublicFiles(files);
    }

    public FileResponse downloadInfo(UUID fileId) throws Exception {
        return fileService.downloadInfo(fileId);
    }

    public InputStream downloadStream(UUID fileId) throws Exception {
        return fileService.downloadStream(fileId);
    }

    public List<FileResponse> listFiles() {
        return fileService.listFiles();
    }

    public List<FileResponse> listAccessibleFiles() {
        return fileService.listAccessibleFiles();
    }

    @Transactional
    public void renameFile(UUID fileId, String newName) {
        fileService.rename(fileId, newName);
        activityService.log("FILE", fileId, "documents.activity.item.renamed", payload("newName", newName));
    }

    @Transactional
    public void moveFile(UUID fileId, UUID newFolderId) {
        fileService.move(fileId, newFolderId);
        activityService.log("FILE", fileId, "documents.activity.item.moved");
    }

    @Transactional
    public void updateFilePermission(UUID fileId, Permission permission) {
        fileService.updatePermission(fileId, permission);
        activityService.log("FILE", fileId, "documents.activity.item.permissionUpdated",
            payload("permission", permission.name()));
    }

    @Transactional
    public void deleteFile(UUID fileId) {
        trashService.softDeleteFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.movedToTrash");
    }

    // ══════════════════════════ SEARCH ══════════════════════════

    public List<SearchResultItem> search(String query) {
        return Stream.concat(
                folderService.searchFolders(query).stream(),
                fileService.searchFiles(query).stream()
        ).collect(java.util.stream.Collectors.toList());
    }

    // ══════════════════════════ SHARE ══════════════════════════

    @Transactional
    public void shareItem(UUID itemId, String type, ShareRequest request) {
        shareService.shareItem(itemId, type, request);
        int shareCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        activityService.log(type, itemId, "documents.activity.item.shared", payload("count", shareCount));
    }

    @Transactional
    public void unshareItem(UUID itemId, String type, ShareRequest request) {
        shareService.unshareItem(itemId, type, request);
        int unshareCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        activityService.log(type, itemId, "documents.activity.item.unshared", payload("count", unshareCount));
    }

    public List<SharedUserResponse> getSharedUsers(UUID itemId, String type) {
        return shareService.getSharedUsers(itemId, type);
    }

    @Transactional
    public void updateSharePermission(UUID shareId, SharePermission permission) {
        Share share = shareService.updateSharePermission(shareId, permission);
        activityService.log(share.getTargetType(), share.getTargetId(), "documents.activity.item.sharePermissionUpdated",
            payload("permission", permission.name()));
    }

    @Transactional
    public void removeShare(UUID shareId) {
        Share share = shareService.removeShare(shareId);
        activityService.log(share.getTargetType(), share.getTargetId(), "documents.activity.item.shareRemoved");
    }

    // ══════════════════════════ FAVORITE ══════════════════════════

    @Transactional
    public boolean toggleFavorite(UUID targetId, String type) {
        boolean favorite = favoriteService.toggle(targetId, type);
        activityService.log(type, targetId,
            favorite ? "documents.activity.item.favorited" : "documents.activity.item.unfavorited");
        return favorite;
    }

    public List<FavoriteItemResponse> listFavorites() {
        return favoriteService.listFavorites();
    }

    // ══════════════════════════ TRASH ══════════════════════════

    public List<TrashItemResponse> listTrash() {
        return trashService.listTrash();
    }

    @Transactional
    public void restoreFile(UUID fileId) {
        trashService.restoreFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.restored");
    }

    @Transactional
    public void restoreFolder(UUID folderId) {
        trashService.restoreFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.restored");
    }

    @Transactional
    public void permanentDeleteFile(UUID fileId) throws Exception {
        trashService.permanentDeleteFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.permanentlyDeleted");
    }

    @Transactional
    public void permanentDeleteFolder(UUID folderId) throws Exception {
        trashService.permanentDeleteFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.permanentlyDeleted");
    }

    public List<DocumentActivityResponse> listItemActivities(UUID itemId, String type) {
        return activityService.listActivities(itemId, type);
    }

    @Transactional
    public void emptyTrash() throws Exception {
        trashService.emptyTrash();
    }

    // ══════════════════════════ BATCH ══════════════════════════

    @Transactional
    public BatchDeleteResponse batchDelete(List<UUID> fileIds, List<UUID> folderIds) throws Exception {
        UUID requesterId = permissionHelper.getCurrentUserId();
        List<UUID> successFiles = new ArrayList<>();
        List<UUID> successFolders = new ArrayList<>();
        List<BatchDeleteResponse.FailedItem> failed = new ArrayList<>();
        int total = (fileIds != null ? fileIds.size() : 0) + (folderIds != null ? folderIds.size() : 0);

        if (fileIds != null) {
            for (UUID fileId : fileIds) {
                try {
                    fileService.forceDelete(fileId, requesterId);
                    successFiles.add(fileId);
                    activityService.log("FILE", fileId, "documents.activity.item.permanentlyDeleted");
                } catch (Exception e) {
                    failed.add(BatchDeleteResponse.FailedItem.builder()
                            .id(fileId).type("file").reason(e.getMessage()).build());
                }
            }
        }
        if (folderIds != null) {
            for (UUID folderId : folderIds) {
                try {
                    trashService.softDeleteFolder(folderId);
                    successFolders.add(folderId);
                    activityService.log("FOLDER", folderId, "documents.activity.item.movedToTrash");
                } catch (Exception e) {
                    failed.add(BatchDeleteResponse.FailedItem.builder()
                            .id(folderId).type("folder").reason(e.getMessage()).build());
                }
            }
        }
        return BatchDeleteResponse.builder()
                .totalRequested(total)
                .successCount(successFiles.size() + successFolders.size())
                .failureCount(failed.size())
                .successfulFileIds(successFiles)
                .successfulFolderIds(successFolders)
                .failedItems(failed)
                .build();
    }

    @Transactional
    public BatchMoveResponse batchMove(List<UUID> fileIds, List<UUID> folderIds, UUID destinationFolderId) {
        List<UUID> successFiles = new ArrayList<>();
        List<UUID> successFolders = new ArrayList<>();
        List<BatchMoveResponse.FailedItem> failed = new ArrayList<>();
        int total = (fileIds != null ? fileIds.size() : 0) + (folderIds != null ? folderIds.size() : 0);

        if (fileIds != null) {
            for (UUID fileId : fileIds) {
                try {
                    fileService.move(fileId, destinationFolderId);
                    successFiles.add(fileId);
                    activityService.log("FILE", fileId, "documents.activity.item.moved");
                } catch (Exception e) {
                    failed.add(BatchMoveResponse.FailedItem.builder()
                            .id(fileId).type("file").reason(e.getMessage()).build());
                }
            }
        }
        if (folderIds != null) {
            for (UUID folderId : folderIds) {
                try {
                    folderService.move(folderId, destinationFolderId);
                    successFolders.add(folderId);
                    activityService.log("FOLDER", folderId, "documents.activity.item.moved");
                } catch (Exception e) {
                    failed.add(BatchMoveResponse.FailedItem.builder()
                            .id(folderId).type("folder").reason(e.getMessage()).build());
                }
            }
        }
        return BatchMoveResponse.builder()
                .totalRequested(total)
                .successCount(successFiles.size() + successFolders.size())
                .failureCount(failed.size())
                .successfulFileIds(successFiles)
                .successfulFolderIds(successFolders)
                .failedItems(failed)
                .build();
    }

    private Map<String, Object> payload(String key, Object value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(key, value);
        return payload;
    }
}
