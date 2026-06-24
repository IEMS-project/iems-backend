package com.iems.aiservice.service;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.service.agent.ProjectAgentFactsService;
import com.iems.aiservice.service.agent.ProjectApiClient;
import com.iems.aiservice.service.agent.ProjectApiToolRegistry;
import com.iems.aiservice.service.agent.ProjectIssueToolService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAgentFactsServiceTest {

    private final ProjectApiToolRegistry registry = new ProjectApiToolRegistry();
    private final ProjectApiClient apiClient = mock(ProjectApiClient.class);
    private final ProjectIssueToolService issueToolService = mock(ProjectIssueToolService.class);
    private final ProjectAgentFactsService service = new ProjectAgentFactsService(registry, apiClient, issueToolService);

    @Test
    void dailyPlanShouldPreferMyIssuesWhenQuestionTargetsCurrentUser() {
        AgentChatRequest request = new AgentChatRequest("viec cua toi hom nay", "conv-1", "project-1", List.of());
        when(issueToolService.getMyProjectIssues("project-1", "Bearer token"))
                .thenReturn(List.of(Map.of("issueKey", "IEMS-1")));

        Map<String, Object> facts = service.resolveFacts(request, "Bearer token", AgentIntent.DAILY_PLAN);

        assertTrue(facts.containsKey("my_issues"));
        verify(issueToolService).getMyProjectIssues("project-1", "Bearer token");
        verify(issueToolService, never()).getProjectIssues("project-1", "Bearer token");
    }

    @Test
    void sprintReportShouldUseSprintTools() {
        AgentChatRequest request = new AgentChatRequest("sprint nay the nao", "conv-1", "project-1", List.of());
        when(apiClient.listSprints("project-1", "Bearer token"))
                .thenReturn(List.of(Map.of("id", "sprint-1", "status", "ACTIVE")));
        when(apiClient.getSprintIssues("project-1", "sprint-1", "Bearer token"))
                .thenReturn(List.of(Map.of("issueKey", "IEMS-2")));
        when(apiClient.getSprintBurndown("project-1", "sprint-1", "Bearer token"))
                .thenReturn(Map.of("remaining", 3));

        Map<String, Object> facts = service.resolveFacts(request, "Bearer token", AgentIntent.SPRINT_REPORT);

        assertTrue(facts.containsKey("sprints"));
        assertTrue(facts.containsKey("sprint_issues"));
        assertTrue(facts.containsKey("sprint_burndown"));
    }

    @Test
    void missingProjectIdShouldNotCallProjectService() {
        AgentChatRequest request = new AgentChatRequest("hom nay toi can lam gi", "conv-1", null, List.of());

        Map<String, Object> facts = service.resolveFacts(request, "Bearer token", AgentIntent.DAILY_PLAN);

        assertTrue(facts.isEmpty());
        verify(issueToolService, never()).getProjectIssues("project-1", "Bearer token");
        verify(apiClient, never()).listSprints("project-1", "Bearer token");
    }
}
