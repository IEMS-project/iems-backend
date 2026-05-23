package com.iems.documentservice.service;

import com.iems.documentservice.client.AiServiceFeignClient;
import com.iems.documentservice.dto.request.AiIndexCommandRequest;
import com.iems.documentservice.entity.ProjectDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIndexingService {

    private final AiServiceFeignClient aiServiceFeignClient;
    private final ObjectStorageService objectStorageService;

    @Async
    public void dispatchIndex(ProjectDocument doc) {
        try {
            String downloadUrl = objectStorageService.presignGetUrl(doc.getCloudinaryPath());
            log.info("Async Dispatch INDEX docId={} projectId={} fileName={} fileType={} urlPresent={}",
                    doc.getId(),
                    doc.getProjectId(),
                    doc.getFileName(),
                    doc.getFileType(),
                    downloadUrl != null && !downloadUrl.isBlank());
            aiServiceFeignClient.dispatchIndexCommand(AiIndexCommandRequest.builder()
                    .projectId(doc.getProjectId().toString())
                    .documentId(doc.getId().toString())
                    .operation("INDEX")
                    .fileName(doc.getFileName())
                    .fileType(doc.getFileType())
                    .downloadUrl(downloadUrl)
                    .build());
            log.info("Async Dispatch INDEX completed for doc {}", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to async dispatch INDEX for doc {}: {}", doc.getId(), e.getMessage());
        }
    }

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
