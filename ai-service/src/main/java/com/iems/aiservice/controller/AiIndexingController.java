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
import java.util.Base64;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

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
                if ((request.downloadUrl() == null || request.downloadUrl().isBlank())
                        && (request.contentBase64() == null || request.contentBase64().isBlank())) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "downloadUrl or contentBase64 is required for INDEX operation");
                }
                try {
                    if (request.contentBase64() != null && !request.contentBase64().isBlank()) {
                        documentContextService.indexUploadedDocument(
                                request.projectId(),
                                request.documentId(),
                                request.fileName(),
                                request.fileType(),
                                Base64.getDecoder().decode(request.contentBase64()));
                    } else {
                        documentContextService.indexDocument(
                                request.projectId(),
                                request.documentId(),
                                request.fileName(),
                                request.fileType(),
                                request.downloadUrl());
                    }
                } catch (IllegalArgumentException ex) {
                    throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
                } catch (Exception ex) {
                    throw new ResponseStatusException(BAD_GATEWAY,
                            "Unable to index document: " + ex.getMessage(), ex);
                }
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
