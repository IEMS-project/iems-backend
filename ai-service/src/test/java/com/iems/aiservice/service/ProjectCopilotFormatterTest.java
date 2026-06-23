package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.service.agent.ProjectIssueToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectCopilotFormatterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ProjectIssueToolService service = new ProjectIssueToolService("http://localhost:1");

    @Test
    @SuppressWarnings("unchecked")
    void dailyPlanShouldReturnTopFiveIssues() throws Exception {
        Method method = ProjectIssueToolService.class.getDeclaredMethod(
                "buildDailyPlan", List.class, Map.class, Map.class, LocalDate.class);
        method.setAccessible(true);

        String json = (String) method.invoke(service, sampleIssues(7), priorities(), statuses(), LocalDate.now());
        Map<String, Object> response = JSON.readValue(json, Map.class);

        assertEquals("daily_plan", response.get("type"));
        assertEquals(5, ((List<?>) response.get("issues")).size());
        assertFalse(json.contains("projectId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskAnalysisShouldIncludeRiskLevelAndActions() throws Exception {
        Method method = ProjectIssueToolService.class.getDeclaredMethod(
                "buildProjectRiskReview", List.class, Map.class, Map.class, LocalDate.class);
        method.setAccessible(true);

        String json = (String) method.invoke(service, sampleIssues(3), priorities(), statuses(), LocalDate.now());
        Map<String, Object> response = JSON.readValue(json, Map.class);

        assertEquals("risk_analysis", response.get("type"));
        assertTrue(String.valueOf(response.get("summary")).contains("Mức rủi ro"));
        assertFalse(((List<?>) response.get("actions")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignIssueShouldReturnConfirmationBeforeApiCall() throws Exception {
        String json = service.handleIssueAction("gán IEMS2-12 cho Nguyễn Văn A", "project-1", "Bearer token");
        Map<String, Object> response = JSON.readValue(json, Map.class);

        assertEquals("confirmation", response.get("type"));
        assertTrue(String.valueOf(response.get("summary")).contains("chỉ thực hiện sau khi bạn xác nhận"));
    }

    private List<Map<String, Object>> sampleIssues(int count) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            issues.add(Map.of(
                    "issueKey", "IEMS2-" + i,
                    "title", "Task " + i,
                    "statusId", i % 2 == 0 ? "doing" : "todo",
                    "priorityId", i <= 3 ? "high" : "medium",
                    "dueDate", LocalDate.now().minusDays(i).toString()));
        }
        return issues;
    }

    private Map<String, String> priorities() {
        return Map.of("high", "High", "medium", "Medium");
    }

    private Map<String, String> statuses() {
        return Map.of("todo", "To Do", "doing", "In Progress");
    }
}
