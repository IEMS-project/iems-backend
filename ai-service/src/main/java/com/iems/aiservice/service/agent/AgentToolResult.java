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

    /**
     * Returns answer for agent tool result processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param answer the answer parameter
     * @return the answer result
     */
    public static AgentToolResult answer(String answer) {
        return new AgentToolResult(true, answer, List.of(), Map.of());
    }

    /**
     * Returns error for agent tool result processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param answer the answer parameter
     * @return the error result
     */
    public static AgentToolResult error(String answer) {
        return new AgentToolResult(false, answer, List.of(), Map.of());
    }
}
