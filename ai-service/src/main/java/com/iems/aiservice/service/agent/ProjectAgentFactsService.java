package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.model.agent.AgentIntent;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProjectAgentFactsService {

    private final ProjectApiToolRegistry toolRegistry;
    private final ProjectApiClient projectApiClient;
    private final ProjectIssueToolService projectIssueToolService;

    public ProjectAgentFactsService(ProjectApiToolRegistry toolRegistry,
            ProjectApiClient projectApiClient,
            ProjectIssueToolService projectIssueToolService) {
        this.toolRegistry = toolRegistry;
        this.projectApiClient = projectApiClient;
        this.projectIssueToolService = projectIssueToolService;
    }

    public Map<String, Object> resolveFacts(AgentChatRequest request, String authorization, AgentIntent intent) {
        if (request.projectId() == null || request.projectId().isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("intent", intent.name());
        facts.put("projectScope", "current_project");
        facts.put("availableReadTools", toolRegistry.readOnlyTools().stream()
                .map(ProjectApiToolRegistry.ProjectApiTool::name)
                .toList());

        switch (intent) {
            case DAILY_PLAN -> addDailyPlanFacts(facts, request, authorization);
            case SPRINT_REPORT, SPRINT_SUMMARY -> addSprintFacts(facts, request, authorization);
            case MEMBER_WORKLOAD -> addToolResult(facts, "member_workload",
                    () -> projectIssueToolService.getMemberWorkload(request.projectId(), authorization));
            case RISK_ANALYSIS, DEADLINE_CHECK -> addToolResult(facts, "risk_signals",
                    () -> projectIssueToolService.getRiskSignals(request.projectId(), authorization));
            case ISSUE_SEARCH, ISSUE_QUERY -> addIssueFacts(facts, request, authorization);
            case PROJECT_SUMMARY, CONTEXTUAL_PROJECT_CHAT -> {
                addToolResult(facts, "project_overview",
                        () -> projectIssueToolService.getProjectOverview(request.projectId(), authorization));
                addToolResult(facts, "risk_signals",
                        () -> projectIssueToolService.getRiskSignals(request.projectId(), authorization));
                addToolResult(facts, "member_workload",
                        () -> projectIssueToolService.getMemberWorkload(request.projectId(), authorization));
            }
            default -> {
            }
        }

        return facts;
    }

    private void addDailyPlanFacts(Map<String, Object> facts, AgentChatRequest request, String authorization) {
        boolean myOnly = isMyWorkQuestion(request.question());
        addToolResult(facts, myOnly ? "my_issues" : "issues",
                () -> myOnly
                        ? projectIssueToolService.getMyProjectIssues(request.projectId(), authorization)
                        : projectIssueToolService.getProjectIssues(request.projectId(), authorization));
        facts.put("today", LocalDate.now().toString());
        facts.put("selectionHint", myOnly
                ? "Prioritize open tasks assigned to the current user, especially overdue or due today."
                : "Prioritize open tasks that are overdue, due today, high priority, blocked, or unassigned.");
    }

    private void addIssueFacts(Map<String, Object> facts, AgentChatRequest request, String authorization) {
        boolean myOnly = isMyWorkQuestion(request.question());
        addToolResult(facts, myOnly ? "my_issues" : "issues",
                () -> myOnly
                        ? projectIssueToolService.getMyProjectIssues(request.projectId(), authorization)
                        : projectIssueToolService.getProjectIssues(request.projectId(), authorization));
    }

    private void addSprintFacts(Map<String, Object> facts, AgentChatRequest request, String authorization) {
        List<Map<String, Object>> sprints = safeList(() -> projectApiClient.listSprints(request.projectId(), authorization));
        facts.put("sprints", limitList(sprints, 10));

        String sprintId = chooseSprintId(sprints);
        if (sprintId == null) {
            facts.put("sprintHint", "No sprint id was available. Ask the user to choose a sprint if they need sprint details.");
            return;
        }

        String selectedSprintId = sprintId;
        addToolResult(facts, "sprint_issues",
                () -> limitList(projectApiClient.getSprintIssues(request.projectId(), selectedSprintId, authorization), 20));
        addToolResult(facts, "sprint_burndown",
                () -> projectApiClient.getSprintBurndown(request.projectId(), selectedSprintId, authorization));
    }

    private static boolean isMyWorkQuestion(String question) {
        String text = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return text.contains("của tôi") || text.contains("của mình") || text.contains("toi")
                || text.contains("mình") || text.contains("my ");
    }

    private static String chooseSprintId(List<Map<String, Object>> sprints) {
        for (Map<String, Object> sprint : sprints) {
            String status = stringValue(sprint.get("status"));
            if (status != null && (status.equalsIgnoreCase("ACTIVE") || status.equalsIgnoreCase("IN_PROGRESS"))) {
                return stringValue(sprint.get("id"));
            }
        }
        return sprints.isEmpty() ? null : stringValue(sprints.get(0).get("id"));
    }

    private static void addToolResult(Map<String, Object> facts, String key, ToolCall call) {
        try {
            Object value = call.execute();
            facts.put(key, value instanceof List<?> list ? limitList(list, 20) : value);
        } catch (Exception ex) {
            facts.put(key + "Error", "Không lấy được dữ liệu project-service lúc này.");
        }
    }

    private static List<Map<String, Object>> safeList(ListToolCall call) {
        try {
            return call.execute();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<?> limitList(List<?> items, int limit) {
        if (items == null || items.size() <= limit) {
            return items == null ? List.of() : items;
        }
        return items.subList(0, limit);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @FunctionalInterface
    interface ToolCall {
        Object execute();
    }

    @FunctionalInterface
    interface ListToolCall {
        List<Map<String, Object>> execute();
    }
}
