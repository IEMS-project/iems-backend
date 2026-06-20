package com.iems.documentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.documentservice.client.UserServiceFeignClient;
import com.iems.documentservice.dto.response.DocumentActivityResponse;
import com.iems.documentservice.entity.DocumentActivity;
import com.iems.documentservice.entity.StoredFile;
import com.iems.documentservice.exception.AppException;
import com.iems.documentservice.repository.DocumentActivityRepository;
import com.iems.documentservice.repository.FolderRepository;
import com.iems.documentservice.repository.ProjectDocumentRepository;
import com.iems.documentservice.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
class ActivityServiceTest {

    @Mock
    private DocumentActivityRepository documentActivityRepository;

    @Mock
    private PermissionHelper permissionHelper;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActivityService activityService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        activityService = new ActivityService(
                documentActivityRepository,
                permissionHelper,
                folderRepository,
                storedFileRepository,
                projectDocumentRepository,
                userServiceFeignClient,
                objectMapper);
    }

    @Test
    void logShouldUseDefaultMessageForBlankActionKey() {
        UUID actor = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(permissionHelper.getCurrentUserId()).thenReturn(actor);

        activityService.log("file", targetId, "   ");

        ArgumentCaptor<DocumentActivity> captor = ArgumentCaptor.forClass(DocumentActivity.class);
        verify(documentActivityRepository).save(captor.capture());
        assertEquals("FILE", captor.getValue().getTargetType());
        assertEquals(targetId, captor.getValue().getTargetId());
        assertEquals("documents.activity.item.updated", captor.getValue().getMessage());
        assertEquals(actor, captor.getValue().getActorUserId());
    }

    @Test
    void listActivitiesShouldEnrichActorFromAccountLookup() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        StoredFile file = StoredFile.builder().id(targetId).ownerId(requester).build();
        DocumentActivity activity = DocumentActivity.builder()
                .id(UUID.randomUUID())
                .targetId(targetId)
                .targetType("FILE")
                .action("updated")
                .message("updated")
                .payload(objectMapper.writeValueAsString(Map.of("status", "ok")))
                .actorUserId(actorId)
                .createdAt(OffsetDateTime.now())
                .build();

        when(permissionHelper.getCurrentUserId()).thenReturn(requester);
        when(storedFileRepository.findById(targetId)).thenReturn(Optional.of(file));
        doNothing().when(permissionHelper).enforceReadPermission(file, requester);
        when(documentActivityRepository.findByTargetIdAndTargetTypeOrderByCreatedAtDesc(targetId, "FILE"))
                .thenReturn(List.of(activity));
        when(userServiceFeignClient.getUsersByAccountIds(any())).thenReturn(ResponseEntity.ok(Map.of(
                "data", List.of(Map.of(
                        "id", actorId.toString(),
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "email", "ada@example.com"
                ))
        )));

        List<DocumentActivityResponse> result = activityService.listActivities(targetId, "file");

        assertEquals(1, result.size());
        assertEquals("Ada Lovelace", result.get(0).getActorName());
        assertEquals("ada@example.com", result.get(0).getActorEmail());
        assertEquals("ok", result.get(0).getPayload().get("status"));
    }

    @Test
    void listActivitiesShouldRejectMissingTarget() {
        UUID requester = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(permissionHelper.getCurrentUserId()).thenReturn(requester);
        when(storedFileRepository.findById(targetId)).thenReturn(Optional.empty());
        when(projectDocumentRepository.existsById(targetId)).thenReturn(false);

        assertThrows(AppException.class, () -> activityService.listActivities(targetId, "file"));
    }

    @Test
    void listActivitiesShouldRejectInvalidTypeInLogHelper() {
        UUID targetId = UUID.randomUUID();
        when(permissionHelper.getCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AppException.class, () -> activityService.log("other", targetId, "create"));
    }
}