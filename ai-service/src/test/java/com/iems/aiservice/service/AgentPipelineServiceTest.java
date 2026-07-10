package com.iems.aiservice.service;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.service.agent.AgentDataCache;
import com.iems.aiservice.service.agent.AgentInputSanitizer;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import com.iems.aiservice.service.agent.AgentPipelineService;
import com.iems.aiservice.service.agent.AgentPlannerService;
import com.iems.aiservice.service.agent.AgentResponseSanitizer;
import com.iems.aiservice.service.agent.PendingActionStore;
import com.iems.aiservice.service.agent.ProjectApiClient;
import com.iems.aiservice.service.agent.ProjectApiToolRegistry;
import com.iems.aiservice.service.agent.ProjectToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPipelineServiceTest {

    private final ProjectApiClient projectApiClient = mock(ProjectApiClient.class);
    private final OpenRouterChatService openRouterChatService = mock(OpenRouterChatService.class);
    private final AgentDataCache cache = new AgentDataCache();
    private final PendingActionStore pendingActionStore = new PendingActionStore();
    private final AgentPlannerService plannerService = new AgentPlannerService(
            new AgentIntentRouterService(),
            pendingActionStore);
    private final ProjectToolExecutor projectToolExecutor = new ProjectToolExecutor(
            projectApiClient,
            cache,
            pendingActionStore,
            new ProjectApiToolRegistry());
    private final AgentPipelineService service = new AgentPipelineService(
            new AgentInputSanitizer(),
            plannerService,
            projectToolExecutor,
            pendingActionStore,
            openRouterChatService,
            new AgentResponseSanitizer());

    @Test
    void issueStatusUpdateShouldProposeThenExecuteAfterConfirmation() {
        when(projectApiClient.listProjectIssues("project-1", "Bearer token"))
                .thenReturn(List.of(issue("issue-8", "IEMS2-8", "User Login", "review", "high")));
        when(projectApiClient.listProjectIssuesPaged("project-1", "Bearer token", 200))
                .thenReturn(List.of());
        when(projectApiClient.listWorkflows("project-1", "Bearer token"))
                .thenReturn(List.of(Map.of("id", "workflow-1", "isDefault", true)));
        when(projectApiClient.listWorkflowStatuses("project-1", "workflow-1", "Bearer token"))
                .thenReturn(List.of(
                        Map.of("id", "review", "name", "Review"),
                        Map.of("id", "done", "name", "Done")));
        when(projectApiClient.changeIssueStatus("project-1", "issue-8", "done", "Bearer token"))
                .thenReturn(Map.of("issueKey", "IEMS2-8", "title", "User Login"));

        AgentChatResponse proposed = service.handle(
                "user-1",
                "conv-1",
                new AgentChatRequest("Chuyen IEMS2-8 sang Done", "conv-1", "project-1", List.of()),
                "Bearer token",
                "",
                "",
                "test-model");

        assertEquals("ISSUE_UPDATE", proposed.intent());
        assertTrue(proposed.answer().contains("Xác nhận"));
        assertFalse(proposed.proposedActions().isEmpty());
        AgentProposedAction action = proposed.proposedActions().getFirst();
        assertEquals("update_issue_status", action.type());
        assertEquals("done", action.payload().get("targetStatusId"));
        verify(projectApiClient, never()).changeIssueStatus("project-1", "issue-8", "done", "Bearer token");

        AgentChatResponse executed = service.handle(
                "user-1",
                "conv-1",
                new AgentChatRequest("dung roi", "conv-1", "project-1", List.of()),
                "Bearer token",
                "",
                "",
                "test-model");

        assertTrue(executed.answer().contains("Đã cập nhật IEMS2-8"));
        verify(projectApiClient).changeIssueStatus("project-1", "issue-8", "done", "Bearer token");
    }

    @Test
    void generalChatShouldUseOpenRouter() {
        when(openRouterChatService.ask("hello", List.of(), "", ""))
                .thenReturn("Xin chào!");

        AgentChatResponse response = service.handle(
                "user-1",
                "conv-1",
                new AgentChatRequest("hello", "conv-1", "project-1", List.of()),
                "Bearer token",
                "",
                "",
                "test-model");

        assertEquals("GENERAL_CHAT", response.intent());
        assertEquals("Xin chào!", response.answer());
    }

    private Map<String, Object> issue(String id, String key, String title, String statusId, String priorityId) {
        return Map.of(
                "id", id,
                "issueKey", key,
                "title", title,
                "statusId", statusId,
                "priorityId", priorityId);
    }
}
