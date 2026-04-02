package com.iems.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

public record IndexingCommandRequest(
        @NotBlank(message = "projectId is required") String projectId,
        @NotBlank(message = "documentId is required") String documentId,
        @NotBlank(message = "operation is required") String operation,
        String fileName,
        String fileType,
        String downloadUrl) {
}
