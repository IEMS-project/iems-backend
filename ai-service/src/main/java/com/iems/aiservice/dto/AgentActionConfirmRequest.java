package com.iems.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgentActionConfirmRequest(
        @NotBlank(message = "conversationId is required") String conversationId,
        @NotBlank(message = "actionId is required") String actionId,
        String projectId,
        List<String> selectedDocumentIds) {
}
