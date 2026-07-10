package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestratorService {

    private final AgentPipelineService agentPipelineService;

    public AgentOrchestratorService(AgentPipelineService agentPipelineService) {
        this.agentPipelineService = agentPipelineService;
    }

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
}
