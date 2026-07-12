package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestratorService {

    private final AgentPipelineService agentPipelineService;

    /**
     * Creates a new agent orchestrator service instance.
     *
     * @param agentPipelineService the agent pipeline service parameter
     */
    public AgentOrchestratorService(AgentPipelineService agentPipelineService) {
        this.agentPipelineService = agentPipelineService;
    }

    /**
     * Handles the agent orchestrator operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @param documentContext the document context parameter
     * @param conversationContext the conversation context parameter
     * @param model the model parameter
     * @return the handle result
     */
    public AgentChatResponse handle(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String documentContext,
            String conversationContext,
            String model) {
        return agentPipelineService.handle(
                userId,
                conversationId,
                request,
                authorization,
                documentContext,
                conversationContext,
                model);
    }

    /**
     * Returns confirm action for agent orchestrator processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param actionId the action id parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @param model the model parameter
     * @return the confirm action result
     */
    public AgentChatResponse confirmAction(String userId,
            String conversationId,
            String actionId,
            String projectId,
            String authorization,
            String model) {
        return agentPipelineService.confirmAction(
                userId,
                conversationId,
                actionId,
                projectId,
                authorization,
                model);
    }
}
