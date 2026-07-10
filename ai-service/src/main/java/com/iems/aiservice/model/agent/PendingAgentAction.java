package com.iems.aiservice.model.agent;

import java.time.Instant;
import java.util.Map;

public record PendingAgentAction(
        String actionId,
        String conversationId,
        String userId,
        String projectId,
        String toolName,
        Map<String, Object> payload,
        String summary,
        Instant createdAt,
        Instant expiresAt) {

    public PendingAgentAction {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        summary = summary == null ? "" : summary;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
