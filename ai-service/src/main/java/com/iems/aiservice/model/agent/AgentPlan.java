package com.iems.aiservice.model.agent;

import java.util.List;
import java.util.Map;

public record AgentPlan(
        AgentAction action,
        AgentIntent intent,
        double confidence,
        String targetTool,
        List<String> requiredInputs,
        Map<String, Object> resolvedInputs,
        List<String> missingInputs,
        String expectedOutput,
        boolean requiresConfirmation,
        String naturalLanguageHint) {

    public AgentPlan {
        action = action == null ? AgentAction.CLARIFY : action;
        intent = intent == null ? AgentIntent.GENERAL_CHAT : intent;
        targetTool = targetTool == null ? "" : targetTool;
        requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
        resolvedInputs = resolvedInputs == null ? Map.of() : Map.copyOf(resolvedInputs);
        missingInputs = missingInputs == null ? List.of() : List.copyOf(missingInputs);
        expectedOutput = expectedOutput == null ? "" : expectedOutput;
        naturalLanguageHint = naturalLanguageHint == null ? "" : naturalLanguageHint;
    }

    public static AgentPlan clarify(AgentIntent intent, String hint, List<String> missingInputs) {
        return new AgentPlan(
                AgentAction.CLARIFY,
                intent,
                0.7,
                "",
                List.of(),
                Map.of(),
                missingInputs,
                "Ask the user for missing information.",
                false,
                hint);
    }
}
