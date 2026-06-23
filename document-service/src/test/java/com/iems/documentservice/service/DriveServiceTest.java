package com.iems.documentservice.service;

import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.FolderContentsResponse;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.dto.response.DocumentActivityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriveServiceTest {

    @Mock private FileService fileService;
    @Mock private FolderService folderService;
    @Mock private ShareService shareService;
    @Mock private FavoriteService favoriteService;
    @Mock private TrashService trashService;
    @Mock private PermissionHelper permissionHelper;
    @Mock private ActivityService activityService;

    private DriveService driveService;

    @BeforeEach
    void setUp() {
        driveService = new DriveService(fileService, folderService, shareService, favoriteService, trashService, permissionHelper, activityService);
    }

    @Test
    void createFolderShouldDelegate() {
        FolderResponse response = FolderResponse.builder().id(UUID.randomUUID()).name("folder").build();
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("folder");
        when(folderService.createFolder(any())).thenReturn(response);

        FolderResponse result = driveService.createFolder(request);

        assertEquals(response, result);
        verify(folderService).createFolder(any());
    }

    @Test
    void listFolderContentsShouldCombineFoldersAndFiles() {
        UUID folderId = UUID.randomUUID();
        List<FolderResponse> folders = List.of(FolderResponse.builder().id(UUID.randomUUID()).name("child").build());
        List<FileResponse> files = List.of(FileResponse.builder().id(UUID.randomUUID()).name("file").build());
        when(folderService.listByParent(folderId)).thenReturn(folders);
        when(fileService.listFilesInFolder(folderId)).thenReturn(files);

        FolderContentsResponse result = driveService.listFolderContents(folderId);

        assertEquals(folders, result.getFolders());
        assertEquals(files, result.getFiles());
    }

    @Test
    void renameFolderShouldDelegateAndLog() {
        UUID folderId = UUID.randomUUID();
        driveService.renameFolder(folderId, "new-name");

        verify(folderService).rename(folderId, "new-name");
        verify(activityService).log("FOLDER", folderId, "documents.activity.item.renamed", Map.of("newName", "new-name"));
    }
}