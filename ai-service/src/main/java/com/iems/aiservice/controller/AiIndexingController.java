package com.iems.aiservice.controller;

import com.iems.aiservice.dto.IndexingCommandRequest;
import com.iems.aiservice.service.DocumentContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/ai/indexing")
@RequiredArgsConstructor
@Slf4j
public class AiIndexingController {

    private final DocumentContextService documentContextService;

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> processEvent(@Valid @RequestBody IndexingCommandRequest request) {
        String operation = request.operation().trim().toUpperCase(Locale.ROOT);
        log.info(
                "Indexing command received operation={} projectId={} documentId={} fileName={} fileType={} hasDownloadUrl={}",
                operation,
                request.projectId(),
                request.documentId(),
                request.fileName(),
                request.fileType(),
                request.downloadUrl() != null && !request.downloadUrl().isBlank());

        return switch (operation) {
            case "INDEX" -> {
                if (request.downloadUrl() == null || request.downloadUrl().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "downloadUrl is required for INDEX operation");
                }
                documentContextService.indexDocument(
                        request.projectId(),
                        request.documentId(),
                        request.fileName(),
                        request.fileType(),
                        request.downloadUrl());
                log.info("Indexing command completed operation={} projectId={} documentId={}",
                        operation, request.projectId(), request.documentId());
                yield ResponseEntity.ok(Map.of("success", true, "operation", operation));
            }
            case "DEINDEX" -> {
                documentContextService.deindexDocument(request.projectId(), request.documentId());
                log.info("Indexing command completed operation={} projectId={} documentId={}",
                        operation, request.projectId(), request.documentId());
                yield ResponseEntity.ok(Map.of("success", true, "operation", operation));
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported operation: " + request.operation());
        };
    }
}
