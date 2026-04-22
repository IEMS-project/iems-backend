package com.iems.aiservice.model.agent;

public record AgentDecision(
                AgentIntent intent,
                double confidence,
                String rationale) {
}
