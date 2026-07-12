package com.iems.documentservice.service;

import com.iems.documentservice.client.AiServiceFeignClient;
import com.iems.documentservice.dto.request.AiIndexCommandRequest;
import com.iems.documentservice.entity.ProjectDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIndexingService {

    private final AiServiceFeignClient aiServiceFeignClient;
    private final ObjectStorageService objectStorageService;

    /**
     * Performs dispatch index for ai indexing processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param doc the doc parameter
     */
    @Async
    public void dispatchIndex(ProjectDocument doc) {
        try {
            byte[] contentBytes;
            try (InputStream inputStream = objectStorageService.download(doc.getStorageKey())) {
                contentBytes = inputStream.readAllBytes();
            }
            log.info("Async Dispatch INDEX docId={} projectId={} fileName={} fileType={} bytes={}",
                    doc.getId(),
                    doc.getProjectId(),
                    doc.getFileName(),
                    doc.getFileType(),
                    contentBytes.length);
            aiServiceFeignClient.dispatchIndexCommand(AiIndexCommandRequest.builder()
                    .projectId(doc.getProjectId().toString())
                    .documentId(doc.getId().toString())
                    .operation("INDEX")
                    .fileName(doc.getFileName())
                    .fileType(doc.getFileType())
                    .contentBase64(Base64.getEncoder().encodeToString(contentBytes))
                    .build());
            log.info("Async Dispatch INDEX completed for doc {}", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to async dispatch INDEX for doc {}: {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * Performs dispatch deindex for ai indexing processing.
     *
     * @param doc the doc parameter
     */
    @Async
    public void dispatchDeindex(ProjectDocument doc) {
        try {
            log.info("Async Dispatch DEINDEX docId={} projectId={}", doc.getId(), doc.getProjectId());
            aiServiceFeignClient.dispatchIndexCommand(AiIndexCommandRequest.builder()
                    .projectId(doc.getProjectId().toString())
                    .documentId(doc.getId().toString())
                    .operation("DEINDEX")
                    .build());
            log.info("Async Dispatch DEINDEX completed for doc {}", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to async dispatch DEINDEX for doc {}: {}", doc.getId(), e.getMessage());
        }
    }
}
