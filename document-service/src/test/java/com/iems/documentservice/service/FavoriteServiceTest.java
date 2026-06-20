package com.iems.documentservice.service;

import com.iems.documentservice.dto.response.FavoriteItemResponse;
import com.iems.documentservice.entity.Favorite;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionHelper permissionHelper;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void toggleFileShouldCreateFavoriteAndReturnTrue() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        StoredFile file = storedFile(fileId, null);
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceReadPermission(file, userId);
        when(favoriteRepository.findByUserIdAndTargetId(userId, fileId)).thenReturn(Optional.empty());

        boolean result = favoriteService.toggle(fileId, "file");

        assertTrue(result);
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(captor.capture());
        assertEquals(userId, captor.getValue().getUserId());
        assertEquals(fileId, captor.getValue().getTargetId());
    }

    @Test
    void toggleFolderShouldDeleteExistingFavoriteAndReturnFalse() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, null);
        Favorite existing = Favorite.builder().id(UUID.randomUUID()).userId(userId).targetId(folderId).build();
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        doNothing().when(permissionHelper).enforceFolderReadPermission(folderId, userId);
        when(favoriteRepository.findByUserIdAndTargetId(userId, folderId)).thenReturn(Optional.of(existing));

        boolean result = favoriteService.toggle(folderId, " folder ");

        assertFalse(result);
        verify(favoriteRepository).delete(existing);
    }

    @Test
    void toggleShouldRejectInvalidType() {
        UUID userId = UUID.randomUUID();
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);

        assertThrows(AppException.class, () -> favoriteService.toggle(UUID.randomUUID(), "other"));
    }

    @Test
    void listFavoritesShouldFilterUnknownTargets() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        Favorite fileFav = Favorite.builder().id(UUID.randomUUID()).userId(userId).targetId(fileId).build();
        Favorite folderFav = Favorite.builder().id(UUID.randomUUID()).userId(userId).targetId(folderId).build();
        Favorite missingFav = Favorite.builder().id(UUID.randomUUID()).userId(userId).targetId(missingId).build();

        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(favoriteRepository.findByUserId(userId)).thenReturn(List.of(fileFav, folderFav, missingFav));
        when(storedFileRepository.findById(fileId)).thenReturn(Optional.of(storedFile(fileId, null)));
        when(storedFileRepository.findById(folderId)).thenReturn(Optional.empty());
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder(folderId, null)));
        when(storedFileRepository.findById(missingId)).thenReturn(Optional.empty());
        when(folderRepository.findById(missingId)).thenReturn(Optional.empty());

        List<FavoriteItemResponse> results = favoriteService.listFavorites();

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(item -> "FILE".equals(item.getTargetType())));
        assertTrue(results.stream().anyMatch(item -> "FOLDER".equals(item.getTargetType())));
    }

    private StoredFile storedFile(UUID id, OffsetDateTime deletedAt) {
        return StoredFile.builder()
                .id(id)
                .name("file.txt")
                .ownerId(UUID.randomUUID())
                .path("/a/file.txt")
                .size(10L)
                .type("text/plain")
                .deletedAt(deletedAt)
                .build();
    }

    private Folder folder(UUID id, OffsetDateTime deletedAt) {
        return Folder.builder()
                .id(id)
                .name("folder")
                .ownerId(UUID.randomUUID())
                .deletedAt(deletedAt)
                .build();
    }
}