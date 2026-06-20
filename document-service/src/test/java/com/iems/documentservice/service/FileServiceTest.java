package com.iems.documentservice.service;

import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.RegisterFileMetadataRequest;
import com.iems.documentservice.dto.response.FileResponse;
import com.iems.documentservice.dto.response.SimpleFileResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ShareRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private PermissionHelper permissionHelper;

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(
                storedFileRepository,
                folderRepository,
                shareRepository,
                favoriteRepository,
                objectStorageService,
                permissionHelper,
                userServiceFeignClient);
    }

    @Test
    void uploadFileShouldStoreAndReturnResponse() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Folder folder = Folder.builder().id(folderId).ownerId(ownerId).name("folder").createdAt(OffsetDateTime.now()).build();
        StoredFile saved = StoredFile.builder()
                .id(fileId)
                .name("doc.txt")
                .folder(folder)
                .ownerId(ownerId)
                .path("document/owners/owner/folder/key-doc.txt")
                .size(11L)
                .type("text/plain")
                .permission(Permission.PUBLIC)
                .createdAt(OffsetDateTime.now())
                .build();
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hello world".getBytes(StandardCharsets.UTF_8));

        when(permissionHelper.getCurrentUserId()).thenReturn(ownerId);
        doNothing().when(permissionHelper).enforceWritePermission(folderId, ownerId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        doNothing().when(objectStorageService).upload(any(), any(MultipartFile.class));
        when(storedFileRepository.save(any(StoredFile.class))).thenReturn(saved);
        when(userServiceFeignClient.getUsersByAccountIds(any())).thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of(
                "id", ownerId.toString(),
                "firstName", "Ada",
                "lastName", "Lovelace",
                "email", "ada@example.com"
        )))));
        when(favoriteRepository.findByUserIdAndTargetIdIn(ownerId, Set.of(fileId))).thenReturn(List.of());

        FileResponse response = fileService.uploadFile(folderId, file);

        assertEquals(fileId, response.getId());
        assertEquals("doc.txt", response.getName());
        assertEquals(folderId, response.getFolderId());
        assertEquals(ownerId, response.getOwnerId());
        assertEquals("Ada Lovelace", response.getOwnerName());
        assertFalse(response.isFavorite());
    }

    @Test
    void uploadChatFilesShouldRejectOversizedImage() {
        UUID ownerId = UUID.randomUUID();
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", content);
        when(permissionHelper.getCurrentUserId()).thenReturn(ownerId);

        assertThrows(AppException.class, () -> fileService.uploadChatFiles("conv-1", new MultipartFile[]{file}));
    }

    @Test
    void listFilesShouldExcludeAvatarsAndMapOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile avatar = StoredFile.builder().id(UUID.randomUUID()).ownerId(ownerId).path("avatar/employee/avatar.png").deletedAt(null).build();
        StoredFile file = StoredFile.builder().id(fileId).ownerId(ownerId).name("doc.txt").path("/docs/doc.txt").size(1L).type("text/plain").createdAt(OffsetDateTime.now()).permission(Permission.PUBLIC).build();

        when(permissionHelper.getCurrentUserId()).thenReturn(ownerId);
        when(storedFileRepository.findByOwnerIdAndDeletedAtIsNull(ownerId)).thenReturn(List.of(avatar, file));
        when(favoriteRepository.findByUserIdAndTargetIdIn(ownerId, Set.of(fileId))).thenReturn(List.of());
        when(userServiceFeignClient.getUsersByAccountIds(any())).thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of(
                "id", ownerId.toString(),
                "firstName", "Grace",
                "lastName", "Hopper",
                "email", "grace@example.com"
        )))));

        List<FileResponse> result = fileService.listFiles();

        assertEquals(1, result.size());
        assertEquals("Grace Hopper", result.get(0).getOwnerName());
        assertEquals("doc.txt", result.get(0).getName());
    }

    @Test
    void listFilesInFolderShouldRejectUnauthorizedAccess() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder folder = Folder.builder().id(folderId).ownerId(UUID.randomUUID()).permission(Permission.PRIVATE).build();

        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(shareRepository.existsByTargetIdAndTargetTypeAndSharedWithUserId(folderId, "FOLDER", userId)).thenReturn(false);

        assertThrows(AppException.class, () -> fileService.listFilesInFolder(folderId));
    }

    @Test
    void renameShouldUpdateNameAndSave() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = StoredFile.builder().id(fileId).ownerId(userId).name("old.txt").build();
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, userId);

        fileService.rename(fileId, "new.txt");

        assertEquals("new.txt", file.getName());
        verify(storedFileRepository).save(file);
    }

    @Test
    void forceDeleteShouldDeleteObjectAndRow() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = StoredFile.builder().id(fileId).ownerId(requesterId).path("/docs/doc.txt").build();
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, requesterId);
        doNothing().when(objectStorageService).delete(file.getPath());

        fileService.forceDelete(fileId, requesterId);

        verify(objectStorageService).delete(file.getPath());
        verify(storedFileRepository).delete(file);
    }
}