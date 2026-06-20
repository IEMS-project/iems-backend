package com.iems.documentservice.service;

import com.iems.documentservice.client.AiServiceFeignClient;
import com.iems.documentservice.dto.request.AiIndexCommandRequest;
import com.iems.documentservice.entity.ProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiIndexingServiceTest {

    @Mock
    private AiServiceFeignClient aiServiceFeignClient;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private AiIndexingService aiIndexingService;

    @Test
    void dispatchIndexShouldSendBase64Payload() throws Exception {
        ProjectDocument document = newDocument();
        byte[] content = "hello ai".getBytes(StandardCharsets.UTF_8);
        when(objectStorageService.download("bucket/key.txt")).thenReturn(new ByteArrayInputStream(content));

        aiIndexingService.dispatchIndex(document);

        ArgumentCaptor<AiIndexCommandRequest> captor = ArgumentCaptor.forClass(AiIndexCommandRequest.class);
        verify(aiServiceFeignClient).dispatchIndexCommand(captor.capture());
        assertEquals(document.getProjectId().toString(), captor.getValue().getProjectId());
        assertEquals(document.getId().toString(), captor.getValue().getDocumentId());
        assertEquals("INDEX", captor.getValue().getOperation());
        assertEquals(document.getFileName(), captor.getValue().getFileName());
        assertEquals(document.getFileType(), captor.getValue().getFileType());
        assertEquals(Base64.getEncoder().encodeToString(content), captor.getValue().getContentBase64());
    }

    @Test
    void dispatchIndexShouldSwallowDownloadFailure() throws Exception {
        ProjectDocument document = newDocument();
        doThrow(new RuntimeException("boom")).when(objectStorageService).download("bucket/key.txt");

        aiIndexingService.dispatchIndex(document);

        verify(aiServiceFeignClient, never()).dispatchIndexCommand(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dispatchDeindexShouldSendDeindexCommand() {
        ProjectDocument document = newDocument();

        aiIndexingService.dispatchDeindex(document);

        ArgumentCaptor<AiIndexCommandRequest> captor = ArgumentCaptor.forClass(AiIndexCommandRequest.class);
        verify(aiServiceFeignClient).dispatchIndexCommand(captor.capture());
        assertEquals("DEINDEX", captor.getValue().getOperation());
    }

    private ProjectDocument newDocument() {
        return ProjectDocument.builder()
                .id(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .fileName("key.txt")
                .fileType("text/plain")
                .storageKey("bucket/key.txt")
                .build();
    }
}