package com.iems.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
                @NotBlank(message = "question is required") String question,
                String conversationId,
                String projectId) {
}
