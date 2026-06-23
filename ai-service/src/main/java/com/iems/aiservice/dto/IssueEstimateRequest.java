package com.iems.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IssueEstimateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description,
        UUID issueTypeId,
        String issueTypeName,
        UUID priorityId,
        String priorityName,
        UUID sprintId) {
}
