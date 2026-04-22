package com.iems.aiservice.dto;

import com.iems.aiservice.model.agent.AgentProposedAction;

import java.time.Instant;
import java.util.List;

public record AgentChatResponse(
                String answer,
                String model,
                String conversationId,
                Instant timestamp,
                String intent,
                double confidence,
                List<AgentProposedAction> proposedActions,
                List<String> sources) {
}
