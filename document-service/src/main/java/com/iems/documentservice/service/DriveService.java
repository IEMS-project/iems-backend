package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.request.ShareRequest;
import com.iems.documentservice.dto.request.RegisterFileMetadataRequest;
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

    /**
     * Creates này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the create folder result
     */
    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request) {
        FolderResponse response = folderService.createFolder(request);
        activityService.log("FOLDER", response.getId(), "documents.activity.item.created", payload("itemName", response.getName()));
        return response;
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FolderResponse> listFolders() {
        return folderService.listFolders();
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @return the list folder contents result
     */
    public FolderContentsResponse listFolderContents(UUID folderId) {
        // fileService.listFilesInFolder đã kiểm tra quyền truy cập folder nội bộ
        List<FolderResponse> folders = folderService.listByParent(folderId);
        List<FileResponse> files = fileService.listFilesInFolder(folderId);
        return FolderContentsResponse.builder().folders(folders).files(files).build();
    }

    /**
     * Updates này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void updateFolderPermission(UUID folderId, Permission permission) {
        folderService.updatePermission(folderId, permission);
        activityService.log("FOLDER", folderId, "documents.activity.item.permissionUpdated",
            payload("permission", permission.name()));
    }

    /**
     * Performs rename folder for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param newName the new name parameter
     */
    @Transactional
    public void renameFolder(UUID folderId, String newName) {
        folderService.rename(folderId, newName);
        activityService.log("FOLDER", folderId, "documents.activity.item.renamed", payload("newName", newName));
    }

    /**
     * Performs move folder for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param newParentId the new parent id parameter
     */
    @Transactional
    public void moveFolder(UUID folderId, UUID newParentId) {
        folderService.move(folderId, newParentId);
        activityService.log("FOLDER", folderId, "documents.activity.item.moved");
    }

    /**
     * Deletes này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     */
    @Transactional
    public void deleteFolderRecursive(UUID folderId) {
        trashService.softDeleteFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.movedToTrash");
    }

    // ══════════════════════════ FILE ══════════════════════════

    /**
     * Uploads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param file the file parameter
     * @return the upload file result
     * @throws Exception if the requested operation cannot be completed
     */
    public FileResponse uploadFile(UUID folderId, MultipartFile file) throws Exception {
        FileResponse response = fileService.uploadFile(folderId, file);
        activityService.log("FILE", response.getId(), "documents.activity.item.created", payload("itemName", response.getName()));
        return response;
    }

    /**
     * Generates này data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param fileName the file name parameter
     * @param contentType the content type parameter
     * @param folderId the folder id parameter
     * @return the generate upload signature result
     */
    public Map<String, Object> generateUploadSignature(String fileName, String contentType, UUID folderId) {
        return fileService.generateUploadSignature(fileName, contentType, folderId);
    }

    /**
     * Registers a new user account.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param request the request parameter
     * @return the register metadata result
     */
    @Transactional
    public FileResponse registerMetadata(RegisterFileMetadataRequest request) {
        FileResponse response = fileService.registerMetadata(request);
        activityService.log("FILE", response.getId(), "documents.activity.item.created", payload("itemName", response.getName()));
        return response;
    }

    /**
     * Uploads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     */
    public List<SimpleFileResponse> uploadFilesAndBuildPublicUrls(UUID folderId, MultipartFile[] files) throws Exception {
        return fileService.uploadBatch(folderId, files);
    }

    /**
     * Uploads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     */
    public List<SimpleFileResponse> uploadChatFiles(String conversationId, MultipartFile[] files) throws Exception {
        return fileService.uploadChatFiles(conversationId, files);
    }

    /**
     * Uploads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param files the files parameter
     * @return the matching result collection
     * @throws Exception if the requested operation cannot be completed
     */
    public List<SimpleFileResponse> uploadFilesToPublicFolder(MultipartFile[] files) throws Exception {
        return fileService.uploadPublicFiles(files);
    }

    /**
     * Downloads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @return the download info result
     * @throws Exception if the requested operation cannot be completed
     */
    public FileResponse downloadInfo(UUID fileId) throws Exception {
        return fileService.downloadInfo(fileId);
    }

    /**
     * Downloads này content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @return the download stream result
     * @throws Exception if the requested operation cannot be completed
     */
    public InputStream downloadStream(UUID fileId) throws Exception {
        return fileService.downloadStream(fileId);
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FileResponse> listFiles() {
        return fileService.listFiles();
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FileResponse> listAccessibleFiles() {
        return fileService.listAccessibleFiles();
    }

    /**
     * Performs rename file for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param newName the new name parameter
     */
    @Transactional
    public void renameFile(UUID fileId, String newName) {
        fileService.rename(fileId, newName);
        activityService.log("FILE", fileId, "documents.activity.item.renamed", payload("newName", newName));
    }

    /**
     * Performs move file for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param newFolderId the new folder id parameter
     */
    @Transactional
    public void moveFile(UUID fileId, UUID newFolderId) {
        fileService.move(fileId, newFolderId);
        activityService.log("FILE", fileId, "documents.activity.item.moved");
    }

    /**
     * Updates này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void updateFilePermission(UUID fileId, Permission permission) {
        fileService.updatePermission(fileId, permission);
        activityService.log("FILE", fileId, "documents.activity.item.permissionUpdated",
            payload("permission", permission.name()));
    }

    /**
     * Deletes này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     */
    @Transactional
    public void deleteFile(UUID fileId) {
        trashService.softDeleteFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.movedToTrash");
    }

    // ══════════════════════════ SEARCH ══════════════════════════

    /**
     * Searches này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param query the query parameter
     * @return the matching result collection
     */
    public List<SearchResultItem> search(String query) {
        return Stream.concat(
                folderService.searchFolders(query).stream(),
                fileService.searchFiles(query).stream()
        ).collect(java.util.stream.Collectors.toList());
    }

    // ══════════════════════════ SHARE ══════════════════════════

    /**
     * Performs share item for này processing.
     *
     * @param itemId the item id parameter
     * @param type the type parameter
     * @param request the request parameter
     */
    @Transactional
    public void shareItem(UUID itemId, String type, ShareRequest request) {
        shareService.shareItem(itemId, type, request);
        int shareCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        activityService.log(type, itemId, "documents.activity.item.shared", payload("count", shareCount));
    }

    /**
     * Performs unshare item for này processing.
     *
     * @param itemId the item id parameter
     * @param type the type parameter
     * @param request the request parameter
     */
    @Transactional
    public void unshareItem(UUID itemId, String type, ShareRequest request) {
        shareService.unshareItem(itemId, type, request);
        int unshareCount = request.getUserIds() == null ? 0 : request.getUserIds().size();
        activityService.log(type, itemId, "documents.activity.item.unshared", payload("count", unshareCount));
    }

    /**
     * Retrieves này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param itemId the item id parameter
     * @param type the type parameter
     * @return the matching result collection
     */
    public List<SharedUserResponse> getSharedUsers(UUID itemId, String type) {
        return shareService.getSharedUsers(itemId, type);
    }

    /**
     * Updates này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param shareId the share id parameter
     * @param permission the permission parameter
     */
    @Transactional
    public void updateSharePermission(UUID shareId, SharePermission permission) {
        Share share = shareService.updateSharePermission(shareId, permission);
        activityService.log(share.getTargetType(), share.getTargetId(), "documents.activity.item.sharePermissionUpdated",
            payload("permission", permission.name()));
    }

    /**
     * Removes này data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param shareId the share id parameter
     */
    @Transactional
    public void removeShare(UUID shareId) {
        Share share = shareService.removeShare(shareId);
        activityService.log(share.getTargetType(), share.getTargetId(), "documents.activity.item.shareRemoved");
    }

    // ══════════════════════════ FAVORITE ══════════════════════════

    /**
     * Returns toggle favorite for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param targetId the target id parameter
     * @param type the type parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    @Transactional
    public boolean toggleFavorite(UUID targetId, String type) {
        boolean favorite = favoriteService.toggle(targetId, type);
        activityService.log(type, targetId,
            favorite ? "documents.activity.item.favorited" : "documents.activity.item.unfavorited");
        return favorite;
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<FavoriteItemResponse> listFavorites() {
        return favoriteService.listFavorites();
    }

    // ══════════════════════════ TRASH ══════════════════════════

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<TrashItemResponse> listTrash() {
        return trashService.listTrash();
    }

    /**
     * Restores này data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     */
    @Transactional
    public void restoreFile(UUID fileId) {
        trashService.restoreFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.restored");
    }

    /**
     * Restores này data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     */
    @Transactional
    public void restoreFolder(UUID folderId) {
        trashService.restoreFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.restored");
    }

    /**
     * Performs permanent delete file for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param fileId the file id parameter
     * @throws Exception if the requested operation cannot be completed
     */
    @Transactional
    public void permanentDeleteFile(UUID fileId) throws Exception {
        trashService.permanentDeleteFile(fileId);
        activityService.log("FILE", fileId, "documents.activity.item.permanentlyDeleted");
    }

    /**
     * Performs permanent delete folder for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param folderId the folder id parameter
     * @throws Exception if the requested operation cannot be completed
     */
    @Transactional
    public void permanentDeleteFolder(UUID folderId) throws Exception {
        trashService.permanentDeleteFolder(folderId);
        activityService.log("FOLDER", folderId, "documents.activity.item.permanentlyDeleted");
    }

    /**
     * Lists này information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param itemId the item id parameter
     * @param type the type parameter
     * @return the matching result collection
     */
    public List<DocumentActivityResponse> listItemActivities(UUID itemId, String type) {
        return activityService.listActivities(itemId, type);
    }

    /**
     * Performs empty trash for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @throws Exception if the requested operation cannot be completed
     */
    @Transactional
    public void emptyTrash() throws Exception {
        trashService.emptyTrash();
    }

    // ══════════════════════════ BATCH ══════════════════════════

    /**
     * Returns batch delete for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param fileIds the file ids parameter
     * @param folderIds the folder ids parameter
     * @return the batch delete result
     * @throws Exception if the requested operation cannot be completed
     */
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
                    trashService.softDeleteFile(fileId);
                    successFiles.add(fileId);
                    activityService.log("FILE", fileId, "documents.activity.item.movedToTrash");
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

    /**
     * Returns batch move for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param fileIds the file ids parameter
     * @param folderIds the folder ids parameter
     * @param destinationFolderId the destination folder id parameter
     * @return the batch move result
     */
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

    /**
     * Returns payload for này processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param key the key parameter
     * @param value the value parameter
     * @return the payload result
     */
    private Map<String, Object> payload(String key, Object value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(key, value);
        return payload;
    }
}
