package com.iems.documentservice.service;

import com.iems.documentservice.dto.response.TrashItemResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ObjectStorageService storageService;

    @Mock
    private PermissionHelper permissionHelper;

    @InjectMocks
    private TrashService trashService;

    @Test
    void softDeleteFileShouldMarkDeletedAndSave() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = file(fileId, null, null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, userId);

        trashService.softDeleteFile(fileId);

        assertTrue(file.getDeletedAt() != null);
        verify(storedFileRepository).save(file);
    }

    @Test
    void restoreFileShouldRestoreParentFolderFirst() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        OffsetDateTime deletedAt = OffsetDateTime.now();
        Folder folder = folder(folderId, deletedAt, null);
        StoredFile file = file(fileId, deletedAt, folder);

        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, userId);
        when(storedFileRepository.findByFolderId(folderId)).thenReturn(List.of());
        when(folderRepository.findByParentId(folderId)).thenReturn(List.of());

        trashService.restoreFile(fileId);

        assertEquals(null, folder.getDeletedAt());
        assertEquals(null, file.getDeletedAt());
        verify(folderRepository).save(folder);
        verify(storedFileRepository).save(file);
    }

    @Test
    void permanentDeleteFileShouldRemoveFromStorageAndRepository() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = file(fileId, OffsetDateTime.now(), null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, userId);
        doNothing().when(storageService).delete(file.getPath());

        trashService.permanentDeleteFile(fileId);

        verify(storageService).delete(file.getPath());
        verify(storedFileRepository).delete(file);
    }

    @Test
    void emptyTrashShouldDeleteAllTrashItems() throws Exception {
        UUID userId = UUID.randomUUID();
        StoredFile file = file(UUID.randomUUID(), OffsetDateTime.now(), null);
        Folder folder = folder(UUID.randomUUID(), OffsetDateTime.now(), null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)).thenReturn(List.of(file));
        when(folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)).thenReturn(List.of(folder));
        doNothing().when(storageService).delete(file.getPath());

        trashService.emptyTrash();

        verify(storageService).delete(file.getPath());
        verify(storedFileRepository).delete(file);
        verify(folderRepository).delete(folder);
    }

    @Test
    void restoreFileShouldRejectActiveFile() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = file(fileId, null, null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceFileOwnerOrFolderOwner(file, userId);

        assertThrows(AppException.class, () -> trashService.restoreFile(fileId));
    }

    @Test
    void listTrashShouldReturnSortedItems() {
        UUID userId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), OffsetDateTime.now().minusDays(1), null);
        StoredFile file = file(UUID.randomUUID(), OffsetDateTime.now(), null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)).thenReturn(List.of(folder));
        when(storedFileRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)).thenReturn(List.of(file));

        List<TrashItemResponse> result = trashService.listTrash();

        assertEquals(2, result.size());
        assertTrue(result.get(0).getDeletedAt().isAfter(result.get(1).getDeletedAt())
                || result.get(0).getDeletedAt().isEqual(result.get(1).getDeletedAt()));
    }

    private StoredFile file(UUID id, OffsetDateTime deletedAt, Folder folder) {
        return StoredFile.builder()
                .id(id)
                .name("file.txt")
                .path("/trash/file.txt")
                .size(10L)
                .type("text/plain")
                .deletedAt(deletedAt)
                .folder(folder)
                .ownerId(UUID.randomUUID())
                .build();
    }

    private Folder folder(UUID id, OffsetDateTime deletedAt, Folder parent) {
        return Folder.builder()
                .id(id)
                .name("folder")
                .deletedAt(deletedAt)
                .parent(parent)
                .ownerId(UUID.randomUUID())
                .build();
    }
}