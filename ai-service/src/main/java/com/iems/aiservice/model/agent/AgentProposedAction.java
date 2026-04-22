package com.iems.aiservice.model.agent;

import java.util.Map;

public record AgentProposedAction(
        String type,
        String summary,
        Map<String, Object> payload) {
}
