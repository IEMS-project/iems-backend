package com.iems.aiservice.service;

import com.iems.aiservice.repository.IssueSuggestionVectorRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueSuggestionServiceTest {

    private final IssueSuggestionService service = new IssueSuggestionService(
            "http://localhost",
            Mockito.mock(OpenRouterEmbeddingService.class),
            Mockito.mock(IssueSuggestionVectorRepository.class));

    @Test
    void suggestStoryPointsUsesNearestFibonacciAverage() {
        Integer point = service.suggestStoryPoints(List.of(
                Map.of("storyPoints", 5),
                Map.of("storyPoints", 8),
                Map.of("storyPoints", 8)));

        assertEquals(8, point);
    }

    @Test
    void suggestStoryPointsFallsBackWhenNoSimilarHistory() {
        assertEquals(3, service.suggestStoryPoints(List.of()));
    }

    @Test
    void buildWorkloadSummaryIgnoresDoneIssuesForOpenLoad() {
        List<Map<String, Object>> members = List.of(
                Map.of("userId", "u1", "userName", "A"),
                Map.of("userId", "u2", "userName", "B"));
        List<Map<String, Object>> issues = List.of(
                Map.of("assigneeId", "u1", "statusId", "todo", "storyPoints", 5),
                Map.of("assigneeId", "u1", "statusId", "done", "storyPoints", 13),
                Map.of("assigneeId", "u2", "statusId", "todo", "storyPoints", 1));

        List<Map<String, Object>> summary = service.buildWorkloadSummary(members, issues, Set.of("done"));

        assertEquals("u2", summary.getFirst().get("memberId"));
        assertEquals(1, summary.getFirst().get("openStoryPoints"));
        assertEquals(5, summary.get(1).get("openStoryPoints"));
        assertEquals(1, summary.get(1).get("completedIssueCount"));
    }
}
