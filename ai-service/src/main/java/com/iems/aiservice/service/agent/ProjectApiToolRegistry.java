package com.iems.aiservice.service.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ProjectApiToolRegistry {

    public enum ToolAccess {
        READ_ONLY,
        CONFIRMATION_REQUIRED
    }

    public record ProjectApiTool(
            String name,
            String method,
            String path,
            String description,
            Set<String> requiredInputs,
            ToolAccess access) {
    }

    private final List<ProjectApiTool> tools = List.of(
            read("list_project_issues", "GET", "/projects/{projectId}/issues",
                    "List issues in the current project.", "projectId"),
            read("get_issue_by_id", "GET", "/projects/{projectId}/issues/{issueId}",
                    "Get one issue by id in the current project.", "projectId", "issueId"),
            read("list_my_issues", "GET", "/projects/{projectId}/issues/my-issues",
                    "List issues assigned to the current user.", "projectId"),
            read("list_sprints", "GET", "/projects/{projectId}/sprints",
                    "List sprints in the current project.", "projectId"),
            read("get_sprint_issues", "GET", "/projects/{projectId}/sprints/{sprintId}/issues",
                    "List issues in one sprint.", "projectId", "sprintId"),
            read("get_sprint_burndown", "GET", "/projects/{projectId}/sprints/{sprintId}/burndown",
                    "Get sprint burndown data.", "projectId", "sprintId"),
            read("list_members", "GET", "/projects/{projectId}/members",
                    "List project members.", "projectId"),
            read("list_priorities", "GET", "/projects/{projectId}/issue-priorities",
                    "List issue priorities for display mapping.", "projectId"),
            read("list_issue_types", "GET", "/projects/{projectId}/issue-types",
                    "List issue types for display mapping.", "projectId"),
            read("list_workflow_statuses", "GET", "/projects/{projectId}/workflows/{workflowId}/statuses",
                    "List workflow statuses for display mapping.", "projectId", "workflowId"),
            new ProjectApiTool("update_issue_status", "PATCH", "/projects/{projectId}/issues/{issueId}/status",
                    "Change issue status after explicit user confirmation.",
                    Set.of("projectId", "issueId", "statusId"), ToolAccess.CONFIRMATION_REQUIRED),
            new ProjectApiTool("assign_issue", "PATCH", "/projects/{projectId}/issues/{issueId}/assign",
                    "Assign issue after explicit user confirmation.",
                    Set.of("projectId", "issueId", "assigneeId"), ToolAccess.CONFIRMATION_REQUIRED),
            new ProjectApiTool("create_issue", "POST", "/projects/{projectId}/issues",
                    "Create issue after explicit user confirmation.",
                    Set.of("projectId"), ToolAccess.CONFIRMATION_REQUIRED));

    /**
     * Returns read only tools for project api tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<ProjectApiTool> readOnlyTools() {
        return tools.stream()
                .filter(tool -> tool.access() == ToolAccess.READ_ONLY)
                .toList();
    }

    /**
     * Returns all tools for project api tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @return the matching result collection
     */
    public List<ProjectApiTool> allTools() {
        return tools;
    }

    /**
     * Returns read for project api tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param name the name parameter
     * @param method the method parameter
     * @param path the path parameter
     * @param description the description parameter
     * @param inputs the inputs parameter
     * @return the read result
     */
    private static ProjectApiTool read(String name, String method, String path, String description, String... inputs) {
        return new ProjectApiTool(name, method, path, description, Set.of(inputs), ToolAccess.READ_ONLY);
    }
}
