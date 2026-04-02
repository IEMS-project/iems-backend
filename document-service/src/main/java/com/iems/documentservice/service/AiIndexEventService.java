package com.iems.documentservice.service;

import com.iems.documentservice.entity.AiDocumentIndexEvent;
import com.iems.documentservice.entity.ProjectDocument;
import com.iems.documentservice.entity.enums.AiIndexEventStatus;
import com.iems.documentservice.entity.enums.AiIndexOperation;
import com.iems.documentservice.repository.AiDocumentIndexEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AiIndexEventService {

    private final AiDocumentIndexEventRepository aiDocumentIndexEventRepository;

    @Transactional
    public void createPendingEvent(ProjectDocument doc, AiIndexOperation operation) {
        aiDocumentIndexEventRepository.save(AiDocumentIndexEvent.builder()
                .projectId(doc.getProjectId())
                .documentId(doc.getId())
                .operation(operation)
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .storagePath(doc.getCloudinaryPath())
                .status(AiIndexEventStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
    }
}
