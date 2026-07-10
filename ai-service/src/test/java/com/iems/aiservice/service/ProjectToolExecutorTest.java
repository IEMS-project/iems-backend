package com.iems.aiservice.service;

import com.iems.aiservice.model.agent.PendingAgentAction;
import com.iems.aiservice.service.agent.AgentDataCache;
import com.iems.aiservice.service.agent.PendingActionStore;
import com.iems.aiservice.service.agent.ProjectApiClient;
import com.iems.aiservice.service.agent.ProjectApiToolRegistry;
import com.iems.aiservice.service.agent.ProjectToolExecutor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectToolExecutorTest {

    private final ProjectApiClient projectApiClient = mock(ProjectApiClient.class);
    private final AgentDataCache cache = new AgentDataCache();
    private final ProjectToolExecutor executor = new ProjectToolExecutor(
            projectApiClient,
            cache,
            new PendingActionStore(),
            new ProjectApiToolRegistry());

    @Test
    void projectIssuesShouldUseCacheUntilEvicted() {
        when(projectApiClient.listProjectIssues("project-1", "Bearer token"))
                .thenReturn(List.of(issue("issue-1", "IEMS2-1")));
        when(projectApiClient.listProjectIssuesPaged("project-1", "Bearer token", 200))
                .thenReturn(List.of());

        assertEquals(1, executor.cachedProjectIssues("project-1", "Bearer token").size());
        assertEquals(1, executor.cachedProjectIssues("project-1", "Bearer token").size());
        verify(projectApiClient, times(1)).listProjectIssues("project-1", "Bearer token");

        cache.evictProjectWriteData("project-1");
        assertEquals(1, executor.cachedProjectIssues("project-1", "Bearer token").size());
        verify(projectApiClient, times(2)).listProjectIssues("project-1", "Bearer token");
    }

    @Test
    void myIssuesCacheShouldBeSeparatedByUser() {
        when(projectApiClient.listMyIssues("project-1", "Bearer token"))
                .thenReturn(List.of(issue("issue-1", "IEMS2-1")));

        executor.cachedMyIssues("project-1", "user-1", "Bearer token");
        executor.cachedMyIssues("project-1", "user-1", "Bearer token");
        executor.cachedMyIssues("project-1", "user-2", "Bearer token");

        verify(projectApiClient, times(2)).listMyIssues("project-1", "Bearer token");
    }

    @Test
    void successfulWriteShouldEvictProjectIssueCache() {
        when(projectApiClient.listProjectIssues("project-1", "Bearer token"))
                .thenReturn(List.of(issue("issue-1", "IEMS2-1")));
        when(projectApiClient.listProjectIssuesPaged("project-1", "Bearer token", 200))
                .thenReturn(List.of());
        when(projectApiClient.changeIssueStatus("project-1", "issue-1", "done", "Bearer token"))
                .thenReturn(Map.of("issueKey", "IEMS2-1", "title", "Task 1"));

        executor.cachedProjectIssues("project-1", "Bearer token");
        executor.executeConfirmedWrite(pendingStatusUpdate(), "Bearer token");
        executor.cachedProjectIssues("project-1", "Bearer token");

        verify(projectApiClient, times(2)).listProjectIssues("project-1", "Bearer token");
    }

    private PendingAgentAction pendingStatusUpdate() {
        Instant now = Instant.now();
        return new PendingAgentAction(
                "action-1",
                "conv-1",
                "user-1",
                "project-1",
                "update_issue_status",
                Map.of(
                        "projectId", "project-1",
                        "issueId", "issue-1",
                        "issueKey", "IEMS2-1",
                        "title", "Task 1",
                        "targetStatusId", "done",
                        "targetStatus", "Done"),
                "Update IEMS2-1",
                now,
                now.plus(PendingActionStore.DEFAULT_TTL));
    }

    private Map<String, Object> issue(String id, String key) {
        return Map.of(
                "id", id,
                "issueKey", key,
                "title", "Task 1");
    }
}
