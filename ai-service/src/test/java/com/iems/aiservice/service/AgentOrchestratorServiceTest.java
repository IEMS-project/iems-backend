package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import com.iems.aiservice.service.agent.AgentOrchestratorService;
import com.iems.aiservice.service.agent.AgentResponseSanitizer;
import com.iems.aiservice.service.agent.ProjectAgentFactsService;
import com.iems.aiservice.service.agent.ProjectIssueToolService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorServiceTest {

    private final AgentIntentRouterService intentRouterService = new AgentIntentRouterService();
    private final OpenRouterChatService openRouterChatService = mock(OpenRouterChatService.class);
    private final ProjectAgentFactsService projectAgentFactsService = mock(ProjectAgentFactsService.class);
    private final ProjectIssueToolService projectIssueToolService = mock(ProjectIssueToolService.class);
    private final AgentResponseSanitizer responseSanitizer = new AgentResponseSanitizer();
    private final AgentOrchestratorService service = new AgentOrchestratorService(
            intentRouterService,
            openRouterChatService,
            projectAgentFactsService,
            projectIssueToolService,
            responseSanitizer,
            new ObjectMapper());

    @Test
    @SuppressWarnings("unchecked")
    void issueUpdateShouldReturnExecutableProposedAction() {
        AgentChatRequest request = new AgentChatRequest(
                "Chuyen IEMS2-8 sang Done",
                "conv-1",
                "project-1",
                List.of());
        when(projectIssueToolService.handleIssueAction(
                "Chuyen IEMS2-8 sang Done",
                "project-1",
                "Bearer token"))
                .thenReturn("""
                        {
                          "type": "confirmation",
                          "title": "Xac nhan doi trang thai issue",
                          "summary": "Ban xac nhan chuyen 1 issue sang Done?",
                          "actions": [{
                            "type": "update_issue_status",
                            "label": "Xac nhan",
                            "payload": {
                              "targetStatusId": "done",
                              "targetStatus": "Done",
                              "issues": [{
                                "issueId": "issue-8",
                                "issueKey": "IEMS2-8",
                                "title": "User Login"
                              }]
                            }
                          }]
                        }
                        """);

        AgentChatResponse response = service.handle(
                "user-1",
                "conv-1",
                request,
                "Bearer token",
                "",
                "",
                "test-model");

        assertEquals("ISSUE_UPDATE", response.intent());
        assertTrue(response.answer().contains("Xac nhan doi trang thai issue"));
        assertFalse(response.proposedActions().isEmpty());

        AgentProposedAction action = response.proposedActions().getFirst();
        assertEquals("update_issue_status", action.type());
        assertEquals("done", action.payload().get("targetStatusId"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) action.payload().get("issues");
        assertEquals("issue-8", issues.getFirst().get("issueId"));
        verify(projectIssueToolService).handleIssueAction(
                "Chuyen IEMS2-8 sang Done",
                "project-1",
                "Bearer token");
    }
}
