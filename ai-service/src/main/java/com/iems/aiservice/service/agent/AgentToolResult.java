package com.iems.aiservice.service.agent;

import com.iems.aiservice.model.agent.AgentProposedAction;

import java.util.List;
import java.util.Map;

public record AgentToolResult(
        boolean success,
        String answer,
        List<AgentProposedAction> proposedActions,
        Map<String, Object> data) {

    public AgentToolResult {
        answer = answer == null ? "" : answer;
        proposedActions = proposedActions == null ? List.of() : List.copyOf(proposedActions);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static AgentToolResult answer(String answer) {
        return new AgentToolResult(true, answer, List.of(), Map.of());
    }

    public static AgentToolResult error(String answer) {
        return new AgentToolResult(false, answer, List.of(), Map.of());
    }
}
