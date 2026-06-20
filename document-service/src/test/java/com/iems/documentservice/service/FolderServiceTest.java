package com.iems.documentservice.service;

import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.request.CreateFolderRequest;
import com.iems.documentservice.dto.response.FolderResponse;
import com.iems.documentservice.entity.Folder;
import com.iems.documentservice.entity.Share;
import com.iems.documentservice.entity.enums.Permission;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.repository.FavoriteRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ShareRepository shareRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PermissionHelper permissionHelper;

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository, shareRepository, favoriteRepository, permissionHelper, userServiceFeignClient);
    }

    @Test
    void createFolderShouldSaveWithParent() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Folder parent = Folder.builder().id(parentId).ownerId(userId).name("parent").createdAt(OffsetDateTime.now()).build();
        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        doNothing().when(permissionHelper).enforceWritePermission(parentId, userId);
        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(userServiceFeignClient.getUsersByAccountIds(any())).thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", userId.toString(), "firstName", "Ada", "lastName", "Lovelace")))));
        when(favoriteRepository.findByUserIdAndTargetIdIn(any(), any())).thenReturn(List.of());

        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("child");
        request.setParentId(parentId);

        FolderResponse response = folderService.createFolder(request);

        assertEquals("child", response.getName());
        assertEquals(parentId, response.getParentId());
        assertEquals(userId, response.getOwnerId());
    }

    @Test
    void moveShouldRejectCircularReference() {
        UUID userId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Folder folder = Folder.builder().id(folderId).ownerId(userId).name("folder").build();
        Folder newParent = Folder.builder().id(UUID.randomUUID()).ownerId(userId).name("parent").build();
        newParent.setParent(folder);

        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        doNothing().when(permissionHelper).enforceFolderOwnerOrParentOwner(folder, userId);
        doNothing().when(permissionHelper).enforceWritePermission(newParent.getId(), userId);
        when(folderRepository.findById(newParent.getId())).thenReturn(Optional.of(newParent));

        assertThrows(AppException.class, () -> folderService.move(folderId, newParent.getId()));
    }

    @Test
    void softDeleteRecursiveShouldCascadeToChildren() {
        Folder root = Folder.builder().id(UUID.randomUUID()).name("root").build();
        Folder child = Folder.builder().id(UUID.randomUUID()).name("child").build();
        when(folderRepository.findByParentIdAndDeletedAtIsNull(root.getId())).thenReturn(List.of(child));

        folderService.softDeleteRecursive(root);

        assertTrue(root.getDeletedAt() != null);
        assertTrue(child.getDeletedAt() != null);
        verify(folderRepository).save(root);
        verify(folderRepository).save(child);
    }

    @Test
    void listFoldersShouldDeduplicateVisibleFolders() {
        UUID userId = UUID.randomUUID();
        UUID ownedId = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Folder owned = Folder.builder().id(ownedId).ownerId(userId).name("owned").createdAt(OffsetDateTime.now()).build();
        Folder shared = Folder.builder().id(sharedId).ownerId(UUID.randomUUID()).name("shared").createdAt(OffsetDateTime.now()).build();
        Folder publicFolder = Folder.builder().id(publicId).ownerId(UUID.randomUUID()).name("public").permission(Permission.PUBLIC).createdAt(OffsetDateTime.now()).build();

        when(permissionHelper.getCurrentUserId()).thenReturn(userId);
        when(folderRepository.findByOwnerIdAndDeletedAtIsNull(userId)).thenReturn(List.of(owned));
        when(shareRepository.findBySharedWithUserId(userId)).thenReturn(List.of(Share.builder().targetId(sharedId).targetType("FOLDER").build()));
        when(folderRepository.findByIdIn(List.of(sharedId))).thenReturn(List.of(shared));
        when(folderRepository.findByPermissionAndDeletedAtIsNull(Permission.PUBLIC)).thenReturn(List.of(publicFolder));
        when(favoriteRepository.findByUserIdAndTargetIdIn(any(), any())).thenReturn(List.of());
        when(userServiceFeignClient.getUsersByAccountIds(any())).thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("id", userId.toString(), "firstName", "Ada", "lastName", "Lovelace")))));

        List<FolderResponse> result = folderService.listFolders();

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(folder -> "owned".equals(folder.getName())));
        assertTrue(result.stream().anyMatch(folder -> "shared".equals(folder.getName())));
        assertTrue(result.stream().anyMatch(folder -> "public".equals(folder.getName())));
    }
}