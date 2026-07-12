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

    /**
     * Creates a new project API client with the configured project service base URL.
     *
     * @param baseUrl the project service base URL
     */
    @Autowired
    public ProjectApiClient(@Value("${ai.agent.project-base-url:http://localhost:8080/project-service}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build());
    }

    /**
     * Creates a new project api service instance.
     *
     * @param restClient the rest client parameter
     */
    ProjectApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list project issues result
     */
    public List<Map<String, Object>> listProjectIssues(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issues", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @param size the size parameter
     * @return the list project issues paged result
     */
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

    /**
     * Retrieves project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param authorization the authorization parameter
     * @return the get issue by id result
     */
    public Map<String, Object> getIssueById(String projectId, String issueId, String authorization) {
        return readDataMap("/projects/{projectId}/issues/{issueId}", authorization, projectId, issueId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list my issues result
     */
    public List<Map<String, Object>> listMyIssues(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issues/my-issues", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list sprints result
     */
    public List<Map<String, Object>> listSprints(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/sprints", authorization, projectId);
    }

    /**
     * Retrieves project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param sprintId the sprint id parameter
     * @param authorization the authorization parameter
     * @return the get sprint by id result
     */
    public Map<String, Object> getSprintById(String projectId, String sprintId, String authorization) {
        return readDataMap("/projects/{projectId}/sprints/{sprintId}", authorization, projectId, sprintId);
    }

    /**
     * Retrieves project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param sprintId the sprint id parameter
     * @param authorization the authorization parameter
     * @return the get sprint issues result
     */
    public List<Map<String, Object>> getSprintIssues(String projectId, String sprintId, String authorization) {
        return readDataList("/projects/{projectId}/sprints/{sprintId}/issues", authorization, projectId, sprintId);
    }

    /**
     * Retrieves project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param sprintId the sprint id parameter
     * @param authorization the authorization parameter
     * @return the get sprint burndown result
     */
    public Map<String, Object> getSprintBurndown(String projectId, String sprintId, String authorization) {
        return readDataMap("/projects/{projectId}/sprints/{sprintId}/burndown", authorization, projectId, sprintId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list members result
     */
    public List<Map<String, Object>> listMembers(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/members", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list issue types result
     */
    public List<Map<String, Object>> listIssueTypes(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issue-types", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list issue priorities result
     */
    public List<Map<String, Object>> listIssuePriorities(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/issue-priorities", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the list workflows result
     */
    public List<Map<String, Object>> listWorkflows(String projectId, String authorization) {
        return readDataList("/projects/{projectId}/workflows", authorization, projectId);
    }

    /**
     * Lists project api information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param workflowId the workflow id parameter
     * @param authorization the authorization parameter
     * @return the list workflow statuses result
     */
    public List<Map<String, Object>> listWorkflowStatuses(String projectId, String workflowId, String authorization) {
        return readDataList("/projects/{projectId}/workflows/{workflowId}/statuses",
                authorization, projectId, workflowId);
    }

    /**
     * Returns change issue status for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param statusId the status id parameter
     * @param authorization the authorization parameter
     * @return the change issue status result
     */
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

    /**
     * Assigns project api data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param assigneeId the assignee id parameter
     * @param authorization the authorization parameter
     * @return the assign issue result
     */
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

    /**
     * Creates project api data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param body the body parameter
     * @param authorization the authorization parameter
     * @return the create issue result
     */
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

    /**
     * Returns read data list for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param path the path parameter
     * @param authorization the authorization parameter
     * @param uriVariables the uri variables parameter
     * @return the read data list result
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readDataList(String path, String authorization, Object... uriVariables) {
        Map<String, Object> response = restClient.get()
                .uri(path, uriVariables)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return response == null ? List.of() : toMapList(response.get("data"));
    }

    /**
     * Returns read data map for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param path the path parameter
     * @param authorization the authorization parameter
     * @param uriVariables the uri variables parameter
     * @return the read data map result
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readDataMap(String path, String authorization, Object... uriVariables) {
        Map<String, Object> response = restClient.get()
                .uri(path, uriVariables)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        return response == null ? Map.of() : copyResponseDataMap(response);
    }

    /**
     * Returns copy response data map for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param response the response parameter
     * @return the copy response data map result
     */
    private Map<String, Object> copyResponseDataMap(Map<String, Object> response) {
        if (response == null || !(response.get("data") instanceof Map<?, ?> data)) {
            return Map.of();
        }
        return copyMap(data);
    }

    /**
     * Returns to map list for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the to map list result
     */
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

    /**
     * Returns copy map for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param source the source parameter
     * @return the copy map result
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<?, ?> source) {
        return new LinkedHashMap<>((Map<String, Object>) source);
    }

    /**
     * Returns first non null for project api processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param values the values parameter
     * @return the first non null result
     */
    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Parses project api data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the parse int value result
     */
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
