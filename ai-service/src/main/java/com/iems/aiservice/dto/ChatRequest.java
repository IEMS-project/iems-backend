package com.iems.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "question is required") String question,
        String conversationId,
        String projectId,
        List<String> selectedDocumentIds) {
}
