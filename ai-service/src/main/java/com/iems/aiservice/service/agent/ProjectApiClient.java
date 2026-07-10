package com.iems.aiservice.service.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProjectApiClient {

    private final RestClient restClient;

    @Autowired
    public ProjectApiClient(@Value("${ai.agent.project-base-url:http://localhost:8080/project-service}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build());
    }

    ProjectApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<Map<String, Object>> listProjectIssues(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issues", authorization, projectId);
    }

    public List<Map<String, Object>> listProjectIssuesPaged(String projectId, String authorization, int size) {
        List<Map<String, Object>> result = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        do {
            int currentPage = page;
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{projectId}/issues/paged")
                            .queryParam("page", currentPage)
                            .queryParam("size", size)
                            .build(projectId))
                    .header("Authorization", authorization)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("data") instanceof Map<?, ?> dataMapRaw)) {
                break;
            }
            Map<String, Object> data = copyMap(dataMapRaw);
            Object content = firstNonNull(data.get("content"), data.get("items"), data.get("data"));
            result.addAll(toMapList(content));
            Integer parsedTotalPages = parseIntValue(data.get("totalPages"));
            totalPages = parsedTotalPages == null ? 1 : parsedTotalPages;
            page++;
        } while (page < totalPages && page < 20);
        return result;
    }

    public Map<String, Object> getIssueById(String projectId, String issueId, String authorization) {
        return readDataMap("/projects/{projectId}/issues/{issueId}", authorization, projectId, issueId);
    }

    public List<Map<String, Object>> listMyIssues(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issues/my-issues", authorization, projectId);
    }

    public List<Map<String, Object>> listSprints(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/sprints", authorization, projectId);
    }

    public Map<String, Object> getSprintById(String projectId, String sprintId, String authorization) {
        return readDataMap("/projects/{projectId}/sprints/{sprintId}", authorization, projectId, sprintId);
    }

    public List<Map<String, Object>> getSprintIssues(String projectId, String sprintId, String authorization) {
        return readDataList("/projects/{projectId}/sprints/{sprintId}/issues", authorization, projectId, sprintId);
    }

    public Map<String, Object> getSprintBurndown(String projectId, String sprintId, String authorization) {
        return readDataMap("/projects/{projectId}/sprints/{sprintId}/burndown", authorization, projectId, sprintId);
    }

    public List<Map<String, Object>> listMembers(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/members", authorization, projectId);
    }

    public List<Map<String, Object>> listIssueTypes(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issue-types", authorization, projectId);
    }

    public List<Map<String, Object>> listIssuePriorities(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issue-priorities", authorization, projectId);
    }

    public List<Map<String, Object>> listWorkflows(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/workflows", authorization, projectId);
    }

    public List<Map<String, Object>> listWorkflowStatuses(String projectId, String workflowId, String authorization) {
        return readDataList("/projects/{projectId}/workflows/{workflowId}/statuses",
                authorization, projectId, workflowId);
    }

    public Map<String, Object> changeIssueStatus(String projectId,
            String issueId,
            String statusId,
            String authorization) {
        Map<String, Object> response = restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/projects/{projectId}/issues/{issueId}/status")
                        .queryParam("statusId", statusId)
                        .build(projectId, issueId))
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : copyResponseDataMap(response);
    }

    public Map<String, Object> assignIssue(String projectId,
            String issueId,
            String assigneeId,
            String authorization) {
        Map<String, Object> response = restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/projects/{projectId}/issues/{issueId}/assign")
                        .queryParam("assigneeId", assigneeId)
                        .build(projectId, issueId))
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : copyResponseDataMap(response);
    }

    public Map<String, Object> createIssue(String projectId,
            Map<String, Object> body,
            String authorization) {
        Map<String, Object> response = restClient.post()
                .uri("/projects/{projectId}/issues", projectId)
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : copyResponseDataMap(response);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readDataList(String path, String authorization, Object... uriVariables) {
        Map<String, Object> response = restClient.get()
                .uri(path, uriVariables)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return response == null ? List.of() : toMapList(response.get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDataMap(String path, String authorization, Object... uriVariables) {
        Map<String, Object> response = restClient.get()
                .uri(path, uriVariables)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : copyResponseDataMap(response);
    }

    private Map<String, Object> copyResponseDataMap(Map<String, Object> response) {
        if (response == null || !(response.get("data") instanceof Map<?, ?> data)) {
            return Map.of();
        }
        return copyMap(data);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(copyMap(map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<?, ?> source) {
        return new LinkedHashMap<>((Map<String, Object>) source);
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer parseIntValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
