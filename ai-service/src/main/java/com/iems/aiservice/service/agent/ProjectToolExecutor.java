package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.model.agent.AgentPlan;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.model.agent.PendingAgentAction;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectToolExecutor {

    private final ProjectApiClient projectApiClient;
    private final AgentDataCache cache;
    private final PendingActionStore pendingActionStore;
    private final ProjectApiToolRegistry toolRegistry;

    /**
     * Creates a new project tool service instance.
     *
     * @param projectApiClient the project api client parameter
     * @param cache the cache parameter
     * @param pendingActionStore the pending action store parameter
     * @param toolRegistry the tool registry parameter
     */
    public ProjectToolExecutor(ProjectApiClient projectApiClient,
            AgentDataCache cache,
            PendingActionStore pendingActionStore,
            ProjectApiToolRegistry toolRegistry) {
        this.projectApiClient = projectApiClient;
        this.cache = cache;
        this.pendingActionStore = pendingActionStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Returns read project for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param plan the plan parameter
     * @param request the request parameter
     * @param userId the user id parameter
     * @param authorization the authorization parameter
     * @return the read project result
     */
    public AgentToolResult readProject(AgentPlan plan,
            AgentChatRequest request,
            String userId,
            String authorization) {
        return switch (plan.intent()) {
            case DAILY_PLAN -> AgentToolResult.answer(buildDailyPlan(request, userId, authorization));
            case ISSUE_QUERY, ISSUE_SEARCH -> AgentToolResult.answer(buildIssueList(request, userId, authorization));
            case MEMBER_WORKLOAD -> AgentToolResult.answer(buildMemberWorkload(request, authorization));
            case RISK_ANALYSIS, DEADLINE_CHECK -> AgentToolResult.answer(buildRiskSignals(request, authorization));
            case PROJECT_SUMMARY, CONTEXTUAL_PROJECT_CHAT -> AgentToolResult.answer(buildProjectSummary(request, authorization));
            case SPRINT_REPORT, SPRINT_SUMMARY -> AgentToolResult.answer(buildSprintReport(request, authorization));
            default -> AgentToolResult.answer(buildProjectSummary(request, authorization));
        };
    }

    /**
     * Returns propose write for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param plan the plan parameter
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param authorization the authorization parameter
     * @return the propose write result
     */
    public AgentToolResult proposeWrite(AgentPlan plan,
            String userId,
            String conversationId,
            String authorization) {
        if (!isRegisteredTool(plan.targetTool())) {
            return AgentToolResult.error("Mình chưa hỗ trợ thao tác này trong pipeline hiện tại.");
        }
        return switch (plan.targetTool()) {
            case "update_issue_status" -> proposeIssueStatusUpdate(plan, userId, conversationId, authorization);
            case "assign_issue" -> proposeAssignIssue(plan, userId, conversationId, authorization);
            case "create_issue" -> AgentToolResult.error("Mình cần tối thiểu issue type và title trước khi tạo issue.");
            default -> AgentToolResult.error("Mình chưa hỗ trợ thao tác này trong pipeline hiện tại.");
        };
    }

    /**
     * Executes the project tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param pendingAction the pending action parameter
     * @param authorization the authorization parameter
     * @return the execute confirmed write result
     */
    public AgentToolResult executeConfirmedWrite(PendingAgentAction pendingAction, String authorization) {
        if (!isRegisteredTool(pendingAction.toolName())) {
            return AgentToolResult.error("Mình chưa hỗ trợ thực thi thao tác này.");
        }
        try {
            return switch (pendingAction.toolName()) {
                case "update_issue_status" -> executeStatusUpdate(pendingAction, authorization);
                case "assign_issue" -> executeAssignIssue(pendingAction, authorization);
                case "create_issue" -> executeCreateIssue(pendingAction, authorization);
                default -> AgentToolResult.error("Mình chưa hỗ trợ thực thi thao tác này.");
            };
        } catch (AgentWriteException ex) {
            return AgentToolResult.error(ex.getMessage());
        } catch (Exception ex) {
            return AgentToolResult.error("Mình chưa cập nhật được dữ liệu project lúc này. Bạn thử lại sau vài giây nhé.");
        }
    }

    /**
     * Returns is registered tool for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param toolName the tool name parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isRegisteredTool(String toolName) {
        return toolRegistry.allTools().stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    /**
     * Returns cached project issues for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the cached project issues result
     */
    public List<Map<String, Object>> cachedProjectIssues(String projectId, String authorization) {
        return cache.getOrLoad(
                "project_issues",
                projectId,
                AgentDataCache.PROJECT_ISSUES_TTL,
                () -> mergeIssues(
                        projectApiClient.listProjectIssues(projectId, authorization),
                        projectApiClient.listProjectIssuesPaged(projectId, authorization, 200)));
    }

    /**
     * Returns cached my issues for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param userId the user id parameter
     * @param authorization the authorization parameter
     * @return the cached my issues result
     */
    public List<Map<String, Object>> cachedMyIssues(String projectId, String userId, String authorization) {
        return cache.getOrLoad(
                "my_issues",
                projectId + ":" + safe(userId),
                AgentDataCache.MY_ISSUES_TTL,
                () -> projectApiClient.listMyIssues(projectId, authorization));
    }

    /**
     * Returns cached priorities for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the cached priorities result
     */
    public Map<String, String> cachedPriorities(String projectId, String authorization) {
        return cache.getOrLoad(
                "priorities",
                projectId,
                AgentDataCache.STATIC_PROJECT_DATA_TTL,
                () -> mapIdToName(projectApiClient.listIssuePriorities(projectId, authorization)));
    }

    /**
     * Returns cached issue types for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the cached issue types result
     */
    public Map<String, String> cachedIssueTypes(String projectId, String authorization) {
        return cache.getOrLoad(
                "issue_types",
                projectId,
                AgentDataCache.STATIC_PROJECT_DATA_TTL,
                () -> mapIdToName(projectApiClient.listIssueTypes(projectId, authorization)));
    }

    /**
     * Returns cached workflow statuses for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the cached workflow statuses result
     */
    public Map<String, String> cachedWorkflowStatuses(String projectId, String authorization) {
        return cache.getOrLoad(
                "workflow_statuses",
                projectId,
                AgentDataCache.STATIC_PROJECT_DATA_TTL,
                () -> {
                    List<Map<String, Object>> workflows = projectApiClient.listWorkflows(projectId, authorization);
                    String workflowId = workflows.stream()
                            .filter(workflow -> Boolean.TRUE.equals(workflow.get("isDefault")))
                            .map(workflow -> stringValue(workflow.get("id")))
                            .filter(id -> id != null && !id.isBlank())
                            .findFirst()
                            .orElseGet(() -> workflows.isEmpty() ? null : stringValue(workflows.get(0).get("id")));
                    if (workflowId == null || workflowId.isBlank()) {
                        return Map.of();
                    }
                    return mapIdToName(projectApiClient.listWorkflowStatuses(projectId, workflowId, authorization));
                });
    }

    /**
     * Returns cached members for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the cached members result
     */
    public List<Map<String, Object>> cachedMembers(String projectId, String authorization) {
        return cache.getOrLoad(
                "members",
                projectId,
                AgentDataCache.STATIC_PROJECT_DATA_TTL,
                () -> projectApiClient.listMembers(projectId, authorization));
    }

    /**
     * Returns propose issue status update for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param plan the plan parameter
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param authorization the authorization parameter
     * @return the propose issue status update result
     */
    private AgentToolResult proposeIssueStatusUpdate(AgentPlan plan,
            String userId,
            String conversationId,
            String authorization) {
        String projectId = stringValue(plan.resolvedInputs().get("projectId"));
        String issueKey = stringValue(plan.resolvedInputs().get("issueKey"));
        String targetStatus = stringValue(plan.resolvedInputs().get("targetStatus"));

        Optional<Map<String, Object>> issue = findIssueByKey(projectId, issueKey, authorization);
        if (issue.isEmpty()) {
            return AgentToolResult.error("Mình không tìm thấy issue " + issueKey + " trong dự án này.");
        }

        Map<String, String> workflowStatuses = cachedWorkflowStatuses(projectId, authorization);
        Map.Entry<String, String> status = findStatus(workflowStatuses, targetStatus);
        if (status == null) {
            return AgentToolResult.error("M\u00ecnh chưa tìm thấy trạng thái \""
                    + cleanDisplay(targetStatus, "bạn yêu cầu")
                    + "\" trong workflow của dự án. Các trạng thái hiện có: "
                    + formatStatusNames(workflowStatuses) + ".");
        }

        String issueId = firstNonBlank(stringValue(issue.get().get("id")), stringValue(issue.get().get("issueId")));
        if (issueId == null) {
            return AgentToolResult.error("Issue " + issueKey + " chưa có mã nội bộ hợp lệ để cập nhật.");
        }

        String actionId = UUID.randomUUID().toString();
        String title = cleanDisplay(stringValue(issue.get().get("title")), "Chưa có tiêu đề");
        Map<String, Object> actionIssue = new LinkedHashMap<>();
        actionIssue.put("issueId", issueId);
        actionIssue.put("issueKey", issueKey);
        actionIssue.put("title", title);
        actionIssue.put("currentStatus", displayStatus(issue.get(), workflowStatuses));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionId", actionId);
        payload.put("projectId", projectId);
        payload.put("issueId", issueId);
        payload.put("issueKey", issueKey);
        payload.put("title", title);
        payload.put("targetStatusId", status.getKey());
        payload.put("targetStatus", status.getValue());
        payload.put("issues", List.of(actionIssue));

        savePending(actionId, conversationId, userId, projectId, "update_issue_status", payload,
                "Cập nhật " + issueKey + " sang " + status.getValue());

        AgentProposedAction proposedAction = new AgentProposedAction(
                "update_issue_status",
                "Xác nhận",
                payload);
        return new AgentToolResult(
                true,
                "Xác nhận đổi trạng thái issue\n\nBạn xác nhận chuyển "
                        + issueKey + " - " + title + " sang \"" + status.getValue()
                        + "\"? Mình chưa thực hiện thay đổi nào.",
                List.of(proposedAction),
                payload);
    }

    /**
     * Returns propose assign issue for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param plan the plan parameter
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param authorization the authorization parameter
     * @return the propose assign issue result
     */
    private AgentToolResult proposeAssignIssue(AgentPlan plan,
            String userId,
            String conversationId,
            String authorization) {
        String projectId = stringValue(plan.resolvedInputs().get("projectId"));
        String issueKey = stringValue(plan.resolvedInputs().get("issueKey"));
        String assigneeText = stringValue(plan.resolvedInputs().get("assigneeText"));

        Optional<Map<String, Object>> issue = findIssueByKey(projectId, issueKey, authorization);
        if (issue.isEmpty()) {
            return AgentToolResult.error("Mình không tìm thấy issue " + issueKey + " trong dự án này.");
        }
        Map<String, Object> member = findMember(projectId, assigneeText, authorization).orElse(null);
        if (member == null) {
            return AgentToolResult.error("Mình chưa xác định được người phụ trách. Bạn gửi email hoặc tên thành viên rõ hơn nhé.");
        }

        String issueId = firstNonBlank(stringValue(issue.get().get("id")), stringValue(issue.get().get("issueId")));
        String assigneeId = firstNonBlank(stringValue(member.get("userId")), stringValue(member.get("accountId")),
                stringValue(member.get("id")));
        if (issueId == null || assigneeId == null) {
            return AgentToolResult.error("Mình thiếu dữ liệu nội bộ để gán issue này.");
        }

        String actionId = UUID.randomUUID().toString();
        String assigneeName = cleanDisplay(firstNonBlank(
                stringValue(member.get("userName")),
                stringValue(member.get("name")),
                stringValue(member.get("userEmail"))), "thành viên đã chọn");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionId", actionId);
        payload.put("projectId", projectId);
        payload.put("issueId", issueId);
        payload.put("issueKey", issueKey);
        payload.put("assigneeId", assigneeId);
        payload.put("assigneeName", assigneeName);

        savePending(actionId, conversationId, userId, projectId, "assign_issue", payload,
                "Gán " + issueKey + " cho " + assigneeName);

        return new AgentToolResult(
                true,
                "Xác nhận gán người phụ trách\n\nBạn xác nhận gán " + issueKey
                        + " cho " + assigneeName + "? Mình chưa thực hiện thay đổi nào.",
                List.of(new AgentProposedAction("assign_issue", "Xác nhận", payload)),
                payload);
    }

    /**
     * Executes the project tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param pendingAction the pending action parameter
     * @param authorization the authorization parameter
     * @return the execute status update result
     * @throws AgentWriteException if the requested operation cannot be completed
     */
    private AgentToolResult executeStatusUpdate(PendingAgentAction pendingAction, String authorization) {
        String projectId = firstNonBlank(pendingAction.projectId(), stringValue(pendingAction.payload().get("projectId")));
        String issueId = stringValue(pendingAction.payload().get("issueId"));
        String statusId = stringValue(pendingAction.payload().get("targetStatusId"));
        String issueKey = cleanDisplay(stringValue(pendingAction.payload().get("issueKey")), "issue");
        String targetStatus = cleanDisplay(stringValue(pendingAction.payload().get("targetStatus")), "trạng thái mới");

        if (projectId == null || issueId == null || statusId == null) {
            return AgentToolResult.error("Mình thiếu dữ liệu để cập nhật issue. Bạn gửi lại yêu cầu giúp mình nhé.");
        }

        Map<String, Object> updated;
        try {
            updated = changeIssueStatusSafely(projectId, issueId, statusId, authorization);
        } catch (AgentWriteException ex) {
            throw new AgentWriteException(statusUpdateFailureMessage(
                    projectId,
                    issueId,
                    issueKey,
                    targetStatus,
                    authorization,
                    ex.getMessage()), ex);
        }
        cache.evictProjectWriteData(projectId);
        String title = cleanDisplay(stringValue(updated.get("title")), stringValue(pendingAction.payload().get("title")));
        return AgentToolResult.answer("Đã cập nhật " + issueKey + " - " + title + " sang " + targetStatus + ".");
    }

    /**
     * Executes the project tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param pendingAction the pending action parameter
     * @param authorization the authorization parameter
     * @return the execute assign issue result
     */
    private AgentToolResult executeAssignIssue(PendingAgentAction pendingAction, String authorization) {
        String projectId = firstNonBlank(pendingAction.projectId(), stringValue(pendingAction.payload().get("projectId")));
        String issueId = stringValue(pendingAction.payload().get("issueId"));
        String assigneeId = stringValue(pendingAction.payload().get("assigneeId"));
        String issueKey = cleanDisplay(stringValue(pendingAction.payload().get("issueKey")), "issue");
        String assigneeName = cleanDisplay(stringValue(pendingAction.payload().get("assigneeName")), "người phụ trách");

        if (projectId == null || issueId == null || assigneeId == null) {
            return AgentToolResult.error("Mình thiếu dữ liệu để gán issue. Bạn gửi lại yêu cầu giúp mình nhé.");
        }

        assignIssueSafely(projectId, issueId, assigneeId, authorization);
        cache.evictProjectWriteData(projectId);
        return AgentToolResult.answer("Đã gán " + issueKey + " cho " + assigneeName + ".");
    }

    /**
     * Executes the project tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param pendingAction the pending action parameter
     * @param authorization the authorization parameter
     * @return the execute create issue result
     */
    @SuppressWarnings("unchecked")
    private AgentToolResult executeCreateIssue(PendingAgentAction pendingAction, String authorization) {
        String projectId = firstNonBlank(pendingAction.projectId(), stringValue(pendingAction.payload().get("projectId")));
        Object body = pendingAction.payload().get("body");
        if (projectId == null || !(body instanceof Map<?, ?> rawBody)) {
            return AgentToolResult.error("Mình thiếu dữ liệu để tạo issue. Bạn gửi lại yêu cầu giúp mình nhé.");
        }
        Map<String, Object> created = createIssueSafely(projectId, new LinkedHashMap<>((Map<String, Object>) rawBody), authorization);
        cache.evictProjectWriteData(projectId);
        return AgentToolResult.answer("Đã tạo issue " + cleanDisplay(stringValue(created.get("issueKey")), "mới") + ".");
    }

    /**
     * Returns change issue status safely for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param statusId the status id parameter
     * @param authorization the authorization parameter
     * @return the change issue status safely result
     * @throws AgentWriteException if the requested operation cannot be completed
     */
    private Map<String, Object> changeIssueStatusSafely(String projectId, String issueId, String statusId, String authorization) {
        try {
            return projectApiClient.changeIssueStatus(projectId, issueId, statusId, authorization);
        } catch (RestClientResponseException ex) {
            throw new AgentWriteException(writeFailureMessage(ex.getStatusCode().value(), "cap nhat trang thai issue"), ex);
        } catch (RestClientException ex) {
            throw new AgentWriteException("Chua ket noi duoc project-service de cap nhat issue. Ban kiem tra service/gateway roi thu lai nhe.", ex);
        }
    }

    /**
     * Assigns project tool data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param assigneeId the assignee id parameter
     * @param authorization the authorization parameter
     * @throws AgentWriteException if the requested operation cannot be completed
     */
    private void assignIssueSafely(String projectId, String issueId, String assigneeId, String authorization) {
        try {
            projectApiClient.assignIssue(projectId, issueId, assigneeId, authorization);
        } catch (RestClientResponseException ex) {
            throw new AgentWriteException(writeFailureMessage(ex.getStatusCode().value(), "gan issue"), ex);
        } catch (RestClientException ex) {
            throw new AgentWriteException("Chua ket noi duoc project-service de gan issue. Ban kiem tra service/gateway roi thu lai nhe.", ex);
        }
    }

    /**
     * Creates project tool data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param body the body parameter
     * @param authorization the authorization parameter
     * @return the create issue safely result
     * @throws AgentWriteException if the requested operation cannot be completed
     */
    private Map<String, Object> createIssueSafely(String projectId, Map<String, Object> body, String authorization) {
        try {
            return projectApiClient.createIssue(projectId, body, authorization);
        } catch (RestClientResponseException ex) {
            throw new AgentWriteException(writeFailureMessage(ex.getStatusCode().value(), "tao issue"), ex);
        } catch (RestClientException ex) {
            throw new AgentWriteException("Chua ket noi duoc project-service de tao issue. Ban kiem tra service/gateway roi thu lai nhe.", ex);
        }
    }

    /**
     * Returns write failure message for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param statusCode the status code parameter
     * @param actionLabel the action label parameter
     * @return the write failure message result
     */
    private String writeFailureMessage(int statusCode, String actionLabel) {
        if (statusCode == 401) {
            return "Phien dang nhap da het han nen minh chua the " + actionLabel + ". Ban dang nhap lai roi bam Allow them lan nua nhe.";
        }
        if (statusCode == 403) {
            return "Tài khoản hiện tại chưa có quyền cập nhật issue trong project này, nên mình không thể " + actionLabel + ".";
        }
        if (statusCode == 404) {
            return "Khong tim thay project, issue hoac trang thai can cap nhat. Ban refresh du lieu roi thu lai nhe.";
        }
        if (statusCode == 400 || statusCode == 409) {
            return "Project-service tu choi thao tac nay vi du lieu khong hop le hoac workflow khong cho phep chuyen trang thai.";
        }
        return "Project-service dang tu choi thao tac nay. Ban thu lai sau vai giay hoac kiem tra log cua project-service.";
    }

    /**
     * Returns status update failure message for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param issueKey the issue key parameter
     * @param targetStatus the target status parameter
     * @param authorization the authorization parameter
     * @param baseMessage the base message parameter
     * @return the status update failure message result
     */
    private String statusUpdateFailureMessage(String projectId,
            String issueId,
            String issueKey,
            String targetStatus,
            String authorization,
            String baseMessage) {
        try {
            Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
            String currentStatus = cachedProjectIssues(projectId, authorization).stream()
                    .filter(issue -> issueId.equals(firstNonBlank(
                            stringValue(issue.get("id")),
                            stringValue(issue.get("issueId")))))
                    .findFirst()
                    .map(issue -> displayStatus(issue, statuses))
                    .orElse("Chưa xác định");
            return baseMessage
                    + "\n\nChi tiết: " + issueKey + " hiện đang ở \"" + currentStatus
                    + "\", yêu cầu chuyển sang \"" + targetStatus + "\"."
                    + "\nCác trạng thái trong workflow hiện có: " + formatStatusNames(statuses) + "."
                    + "\nNếu workflow chỉ cho chuyển qua một số bước trung gian, hãy chọn trạng thái kế tiếp hợp lệ rồi bấm Allow lại.";
        } catch (Exception ignored) {
            return baseMessage + "\n\nMình chưa đọc lại được workflow hiện tại để bổ sung trạng thái hợp lệ. Bạn kiểm tra project-service/gateway rồi thử lại.";
        }
    }

    /**
     * Returns format status names for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param statuses the statuses parameter
     * @return the format status names result
     */
    private String formatStatusNames(Map<String, String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return "chưa lấy được danh sách trạng thái";
        }
        return statuses.values().stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    static class AgentWriteException extends RuntimeException {
        AgentWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param userId the user id parameter
     * @param authorization the authorization parameter
     * @return the build daily plan result
     */
    private String buildDailyPlan(AgentChatRequest request, String userId, String authorization) {
        String projectId = request.projectId();
        boolean myOnly = isMyWorkQuestion(request.question());
        List<Map<String, Object>> issues = myOnly
                ? cachedMyIssues(projectId, userId, authorization)
                : cachedProjectIssues(projectId, authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
        Map<String, String> priorities = cachedPriorities(projectId, authorization);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> top = issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            return "Chưa có issue mở phù hợp để lập kế hoạch hôm nay.";
        }

        StringBuilder out = new StringBuilder("Top 5 issue ưu tiên hôm nay\n\n");
        out.append("| Issue Key | Title | Status | Priority | Due Date | Lý do ưu tiên |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (Map<String, Object> issue : top) {
            out.append("| ")
                    .append(cleanDisplay(stringValue(issue.get("issueKey")), "Chưa có key")).append(" | ")
                    .append(cleanDisplay(stringValue(issue.get("title")), "Chưa có tiêu đề")).append(" | ")
                    .append(displayStatus(issue, statuses)).append(" | ")
                    .append(displayPriority(issue, priorities)).append(" | ")
                    .append(cleanDisplay(stringValue(issue.get("dueDate")), "Chưa có hạn chót")).append(" | ")
                    .append(priorityReason(issue, statuses, priorities, today)).append(" |\n");
        }
        return out.toString().trim();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param userId the user id parameter
     * @param authorization the authorization parameter
     * @return the build issue list result
     */
    private String buildIssueList(AgentChatRequest request, String userId, String authorization) {
        String projectId = request.projectId();
        boolean myOnly = isMyWorkQuestion(request.question());
        List<Map<String, Object>> issues = myOnly
                ? cachedMyIssues(projectId, userId, authorization)
                : cachedProjectIssues(projectId, authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
        Map<String, String> priorities = cachedPriorities(projectId, authorization);

        if (issues.isEmpty()) {
            return "Mình chưa thấy issue nào phù hợp trong dự án này.";
        }

        StringBuilder out = new StringBuilder("Mình tìm thấy ")
                .append(issues.size())
                .append(" issue. Hiển thị tối đa 7 issue đầu tiên:\n");
        issues.stream().limit(7).forEach(issue -> out.append("- ")
                .append(cleanDisplay(stringValue(issue.get("issueKey")), "Chưa có key"))
                .append(" - ")
                .append(cleanDisplay(stringValue(issue.get("title")), "Chưa có tiêu đề"))
                .append(" | status=").append(displayStatus(issue, statuses))
                .append(" | priority=").append(displayPriority(issue, priorities))
                .append(" | due=").append(cleanDisplay(stringValue(issue.get("dueDate")), "Chưa có hạn chót"))
                .append("\n"));
        return out.toString().trim();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the build project summary result
     */
    private String buildProjectSummary(AgentChatRequest request, String authorization) {
        String projectId = request.projectId();
        List<Map<String, Object>> issues = cachedProjectIssues(projectId, authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
        Map<String, String> priorities = cachedPriorities(projectId, authorization);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> openIssues = issues.stream().filter(issue -> !isDone(issue, statuses)).toList();
        List<Map<String, Object>> doneIssues = issues.stream().filter(issue -> isDone(issue, statuses)).toList();
        List<Map<String, Object>> doingIssues = openIssues.stream()
                .filter(issue -> {
                    String status = normalize(displayStatus(issue, statuses));
                    return status.contains("progress") || status.contains("doing") || status.contains("review")
                            || status.contains("dang");
                })
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList();
        List<Map<String, Object>> risks = openIssues.stream()
                .filter(issue -> isOverdue(issue, today) || priorityWeight(displayPriority(issue, priorities)) >= 80
                        || normalize(displayStatus(issue, statuses)).contains("review"))
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList();
        Map<String, Long> byStatus = issues.stream()
                .collect(Collectors.groupingBy(issue -> displayStatus(issue, statuses), LinkedHashMap::new, Collectors.counting()));
        String health = risks.stream().anyMatch(issue -> isOverdue(issue, today))
                ? "Cần chú ý"
                : risks.size() >= 5 ? "Trung bình" : "Ổn";

        StringBuilder out = new StringBuilder();
        out.append("## Tổng quan\n\n")
                .append("- Tổng issue: ").append(issues.size()).append("\n")
                .append("- Issue đang mở: ").append(openIssues.size()).append("\n")
                .append("- Issue đã xong: ").append(doneIssues.size()).append("\n")
                .append("- Sức khỏe hiện tại: ").append(health).append("\n\n");

        out.append("## Thống kê theo trạng thái\n\n");
        byStatus.forEach((status, count) -> out.append("- ").append(status).append(": ").append(count).append("\n"));

        out.append("\n## Việc đang làm\n\n");
        appendIssueBullets(out, doingIssues, statuses, priorities, today);

        out.append("\n## Việc đã xong\n\n");
        appendIssueBullets(out, doneIssues.stream().limit(5).toList(), statuses, priorities, today);

        out.append("\n## Việc quá hạn/rủi ro\n\n");
        appendIssueBullets(out, risks, statuses, priorities, today);

        out.append("\n## Nhận xét sức khỏe dự án\n\n")
                .append("- ").append(health.equals("Cần chú ý")
                        ? "Dự án có issue quá hạn hoặc priority cao, nên chốt owner/ETA và xử lý trước khi nhận thêm việc."
                        : "Dự án chưa có tín hiệu quá hạn nổi bật, tiếp tục giữ nhịp review và cập nhật trạng thái.");
        return out.toString().trim();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the build risk signals result
     */
    private String buildRiskSignals(AgentChatRequest request, String authorization) {
        String projectId = request.projectId();
        List<Map<String, Object>> issues = cachedProjectIssues(projectId, authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
        Map<String, String> priorities = cachedPriorities(projectId, authorization);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> risks = issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .filter(issue -> isOverdue(issue, today) || priorityWeight(displayPriority(issue, priorities)) >= 80)
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList();
        if (risks.isEmpty()) {
            return "Chưa thấy tín hiệu rủi ro nổi bật trong các issue đang mở.";
        }
        String riskLevel = risks.stream().anyMatch(issue -> isOverdue(issue, today))
                ? "Cao"
                : risks.size() >= 4 ? "Trung bình" : "Thấp";
        StringBuilder out = new StringBuilder("## Mức rủi ro\n\n")
                .append(riskLevel)
                .append("\n\n## Issue rủi ro chính\n\n");
        risks.forEach(issue -> out.append("- ")
                .append(cleanDisplay(stringValue(issue.get("issueKey")), "Chưa có key"))
                .append(" - ")
                .append(cleanDisplay(stringValue(issue.get("title")), "Chưa có tiêu đề"))
                .append(": ")
                .append(priorityReason(issue, statuses, priorities, today))
                .append("\n"));
        out.append("\n## Tác động\n\n")
                .append("- Có thể kéo dài review, trễ deadline hoặc làm chậm các task phụ thuộc.\n")
                .append("- Các issue priority cao nếu không chốt sớm sẽ làm lệch kế hoạch sprint.\n\n")
                .append("## Hành động đề xuất\n\n")
                .append("- Chốt ETA cho issue quá hạn trong hôm nay.\n")
                .append("- Ưu tiên xử lý issue Highest/High trước các task medium.\n")
                .append("- Với issue đang Review, gán người chịu trách nhiệm approve hoặc trả feedback rõ ràng.");
        return out.toString().trim();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the build member workload result
     */
    private String buildMemberWorkload(AgentChatRequest request, String authorization) {
        List<Map<String, Object>> issues = cachedProjectIssues(request.projectId(), authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(request.projectId(), authorization);
        Map<String, Long> workload = issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .collect(Collectors.groupingBy(this::assigneeName, LinkedHashMap::new, Collectors.counting()));
        if (workload.isEmpty()) {
            return "Chưa có issue mở để đánh giá workload.";
        }
        List<Map.Entry<String, Long>> ranked = workload.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(7)
                .toList();
        long max = ranked.getFirst().getValue();
        StringBuilder out = new StringBuilder("## Ai đang quá tải\n\n");
        ranked.forEach(entry -> out.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" issue đang mở")
                        .append(entry.getValue() == max && max >= 3 ? " - cần theo dõi" : "")
                        .append("\n"));
        out.append("\n## Ai cần hỗ trợ\n\n");
        ranked.stream()
                .filter(entry -> entry.getValue() == max && max >= 3)
                .forEach(entry -> out.append("- ").append(entry.getKey())
                        .append(" nên được giảm bớt issue priority cao hoặc issue quá hạn.\n"));
        if (max < 3) {
            out.append("- Chưa thấy thành viên nào quá tải rõ ràng theo số issue mở.\n");
        }
        out.append("\n## Nên phân bổ lại việc nào\n\n");
        Map<String, String> priorities = cachedPriorities(request.projectId(), authorization);
        appendIssueBullets(out, issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, LocalDate.now())))
                .limit(5)
                .toList(), statuses, priorities, LocalDate.now());
        return out.toString().trim();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the build sprint report result
     */
    private String buildSprintReport(AgentChatRequest request, String authorization) {
        String normalizedQuestion = normalize(request.question());
        if (normalizedQuestion.contains("standup") || normalizedQuestion.contains("blocker")) {
            return buildStandupReport(request, authorization);
        }
        List<Map<String, Object>> sprints = projectApiClient.listSprints(request.projectId(), authorization);
        if (sprints.isEmpty()) {
            return "Dự án chưa có sprint để báo cáo.";
        }
        Map<String, Object> sprint = sprints.stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(stringValue(item.get("status")))
                        || "IN_PROGRESS".equalsIgnoreCase(stringValue(item.get("status"))))
                .findFirst()
                .orElse(sprints.get(0));
        String sprintId = stringValue(sprint.get("id"));
        List<Map<String, Object>> sprintIssues = sprintId == null
                ? List.of()
                : projectApiClient.getSprintIssues(request.projectId(), sprintId, authorization);
        return "Sprint hiện tại\n\n"
                + "- Sprint: " + cleanDisplay(stringValue(sprint.get("name")), "Chưa có tên") + "\n"
                + "- Status: " + cleanDisplay(stringValue(sprint.get("status")), "Chưa phân loại") + "\n"
                + "- Số issue: " + sprintIssues.size();
    }

    /**
     * Builds project tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @return the build standup report result
     */
    private String buildStandupReport(AgentChatRequest request, String authorization) {
        String projectId = request.projectId();
        List<Map<String, Object>> issues = cachedProjectIssues(projectId, authorization);
        Map<String, String> statuses = cachedWorkflowStatuses(projectId, authorization);
        Map<String, String> priorities = cachedPriorities(projectId, authorization);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> done = issues.stream().filter(issue -> isDone(issue, statuses)).limit(5).toList();
        List<Map<String, Object>> doing = issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .filter(issue -> normalize(displayStatus(issue, statuses)).contains("progress")
                        || normalize(displayStatus(issue, statuses)).contains("review"))
                .limit(5)
                .toList();
        List<Map<String, Object>> risks = issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .filter(issue -> isOverdue(issue, today) || priorityWeight(displayPriority(issue, priorities)) >= 80)
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList();

        StringBuilder out = new StringBuilder("## Đã xong\n\n");
        appendIssueBullets(out, done, statuses, priorities, today);
        out.append("\n## Đang làm\n\n");
        appendIssueBullets(out, doing, statuses, priorities, today);
        out.append("\n## Blocker\n\n");
        List<Map<String, Object>> blockers = risks.stream()
                .filter(issue -> normalize(displayStatus(issue, statuses)).contains("block")
                        || isOverdue(issue, today))
                .toList();
        appendIssueBullets(out, blockers, statuses, priorities, today);
        out.append("\n## Rủi ro\n\n");
        appendIssueBullets(out, risks, statuses, priorities, today);
        out.append("\n## Việc ưu tiên tiếp theo\n\n");
        appendIssueBullets(out, issues.stream()
                .filter(issue -> !isDone(issue, statuses))
                .sorted(Comparator.comparingInt(issue -> -importanceScore(issue, statuses, priorities, today)))
                .limit(5)
                .toList(), statuses, priorities, today);
        return out.toString().trim();
    }

    /**
     * Performs append issue bullets for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param out the out parameter
     * @param issues the issues parameter
     * @param statuses the statuses parameter
     * @param priorities the priorities parameter
     * @param today the today parameter
     */
    private void appendIssueBullets(StringBuilder out,
            List<Map<String, Object>> issues,
            Map<String, String> statuses,
            Map<String, String> priorities,
            LocalDate today) {
        if (issues == null || issues.isEmpty()) {
            out.append("- Chưa có issue phù hợp.\n");
            return;
        }
        issues.forEach(issue -> out.append("- ")
                .append(cleanDisplay(stringValue(issue.get("issueKey")), "Chưa có key"))
                .append(" - ")
                .append(cleanDisplay(stringValue(issue.get("title")), "Chưa có tiêu đề"))
                .append(" | status=").append(displayStatus(issue, statuses))
                .append(" | priority=").append(displayPriority(issue, priorities))
                .append(" | due=").append(cleanDisplay(stringValue(issue.get("dueDate")), "Chưa có hạn chót"))
                .append(" | ").append(priorityReason(issue, statuses, priorities, today))
                .append("\n"));
    }

    /**
     * Finds project tool information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param issueKey the issue key parameter
     * @param authorization the authorization parameter
     * @return the find issue by key result
     */
    private Optional<Map<String, Object>> findIssueByKey(String projectId, String issueKey, String authorization) {
        if (issueKey == null || issueKey.isBlank()) {
            return Optional.empty();
        }
        String normalizedKey = normalize(issueKey);
        return cachedProjectIssues(projectId, authorization).stream()
                .filter(issue -> normalizedKey.equals(normalize(stringValue(issue.get("issueKey")))))
                .findFirst();
    }

    /**
     * Finds project tool information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param workflowStatuses the workflow statuses parameter
     * @param targetStatus the target status parameter
     * @return the find status result
     */
    private Map.Entry<String, String> findStatus(Map<String, String> workflowStatuses, String targetStatus) {
        String target = normalize(targetStatus);
        for (Map.Entry<String, String> entry : workflowStatuses.entrySet()) {
            String name = normalize(entry.getValue());
            if (name.equals(target) || name.contains(target) || target.contains(name)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Finds project tool information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param assigneeText the assignee text parameter
     * @param authorization the authorization parameter
     * @return the find member result
     */
    private Optional<Map<String, Object>> findMember(String projectId, String assigneeText, String authorization) {
        String target = normalize(assigneeText);
        if (target.isBlank()) {
            return Optional.empty();
        }
        return cachedMembers(projectId, authorization).stream()
                .filter(member -> matchesMember(target, member))
                .findFirst();
    }

    /**
     * Returns matches member for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param target the target parameter
     * @param member the member parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean matchesMember(String target, Map<String, Object> member) {
        String email = normalize(stringValue(member.get("userEmail")));
        String name = normalize(stringValue(member.get("userName")));
        return (!email.isBlank() && email.contains(target))
                || (!name.isBlank() && (name.contains(target) || target.contains(name)));
    }

    /**
     * Saves project tool data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Persist the resulting domain changes.</li>
     * </ul>
     *
     * @param actionId the action id parameter
     * @param conversationId the conversation id parameter
     * @param userId the user id parameter
     * @param projectId the project id parameter
     * @param toolName the tool name parameter
     * @param payload the payload parameter
     * @param summary the summary parameter
     */
    private void savePending(String actionId,
            String conversationId,
            String userId,
            String projectId,
            String toolName,
            Map<String, Object> payload,
            String summary) {
        Instant now = Instant.now();
        pendingActionStore.save(new PendingAgentAction(
                actionId,
                conversationId,
                userId,
                projectId,
                toolName,
                payload,
                summary,
                now,
                now.plus(PendingActionStore.DEFAULT_TTL)));
    }

    /**
     * Returns merge issues for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param first the first parameter
     * @param second the second parameter
     * @return the merge issues result
     */
    private List<Map<String, Object>> mergeIssues(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<Map<String, Object>> source : List.of(first, second)) {
            if (source == null) {
                continue;
            }
            for (Map<String, Object> issue : source) {
                String identity = firstNonBlank(stringValue(issue.get("id")), stringValue(issue.get("issueId")),
                        stringValue(issue.get("issueKey")));
                if (identity == null || seen.add(identity)) {
                    result.add(new LinkedHashMap<>(issue));
                }
            }
        }
        return result;
    }

    /**
     * Maps project tool data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param items the items parameter
     * @return the map id to name result
     */
    private Map<String, String> mapIdToName(List<Map<String, Object>> items) {
        Map<String, String> byId = new LinkedHashMap<>();
        if (items == null) {
            return byId;
        }
        for (Map<String, Object> item : items) {
            String id = stringValue(item.get("id"));
            String name = firstNonBlank(stringValue(item.get("name")), stringValue(item.get("title")),
                    stringValue(item.get("statusName")));
            if (id != null && name != null) {
                byId.put(id, name);
            }
        }
        return byId;
    }

    /**
     * Returns importance score for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statuses the statuses parameter
     * @param priorities the priorities parameter
     * @param today the today parameter
     * @return the importance score result
     */
    private int importanceScore(Map<String, Object> issue,
            Map<String, String> statuses,
            Map<String, String> priorities,
            LocalDate today) {
        int score = priorityWeight(displayPriority(issue, priorities));
        if (isOverdue(issue, today)) {
            score += 100;
        } else if (isDueToday(issue, today)) {
            score += 60;
        }
        String status = normalize(displayStatus(issue, statuses));
        if (status.contains("review")) {
            score += 20;
        }
        if (status.contains("block") || status.contains("stuck")) {
            score += 50;
        }
        if (assigneeName(issue).equals("Chưa có người phụ trách")) {
            score += 15;
        }
        return score;
    }

    /**
     * Returns priority reason for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statuses the statuses parameter
     * @param priorities the priorities parameter
     * @param today the today parameter
     * @return the priority reason result
     */
    private String priorityReason(Map<String, Object> issue,
            Map<String, String> statuses,
            Map<String, String> priorities,
            LocalDate today) {
        if (isOverdue(issue, today)) {
            return "Đã quá hạn, cần cập nhật ETA và xử lý ngay.";
        }
        if (isDueToday(issue, today)) {
            return "Đến hạn hôm nay, cần hoàn tất hoặc cập nhật tiến độ.";
        }
        String priority = displayPriority(issue, priorities);
        if (priorityWeight(priority) >= 90) {
            return "Priority cao nhất, có thể ảnh hưởng tiến độ nếu trễ.";
        }
        if (normalize(displayStatus(issue, statuses)).contains("review")) {
            return "Đang ở bước review, nên chốt nhanh để không kéo dài vòng lặp.";
        }
        return "Đang mở và có mức ưu tiên cần theo dõi.";
    }

    /**
     * Returns is done for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statuses the statuses parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isDone(Map<String, Object> issue, Map<String, String> statuses) {
        String status = normalize(displayStatus(issue, statuses));
        String category = normalize(stringValue(issue.get("statusCategory")));
        return status.contains("done") || status.contains("closed") || status.contains("hoan thanh")
                || category.contains("done");
    }

    /**
     * Returns is overdue for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param today the today parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isOverdue(Map<String, Object> issue, LocalDate today) {
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        return due != null && due.isBefore(today);
    }

    /**
     * Returns is due today for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param today the today parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isDueToday(Map<String, Object> issue, LocalDate today) {
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        return due != null && due.isEqual(today);
    }

    /**
     * Returns priority weight for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param priorityName the priority name parameter
     * @return the priority weight result
     */
    private int priorityWeight(String priorityName) {
        String priority = normalize(priorityName);
        if (priority.contains("highest") || priority.contains("critical")) {
            return 100;
        }
        if (priority.contains("high")) {
            return 80;
        }
        if (priority.contains("medium")) {
            return 50;
        }
        if (priority.contains("low")) {
            return 20;
        }
        return 30;
    }

    /**
     * Assigns project tool data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Send the required notification or outbound message.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return the assignee name result
     */
    @SuppressWarnings("unchecked")
    private String assigneeName(Map<String, Object> issue) {
        Object assignee = issue.get("assignee");
        if (assignee instanceof Map<?, ?> map) {
            String name = firstNonBlank(stringValue(((Map<String, Object>) map).get("fullName")),
                    stringValue(((Map<String, Object>) map).get("name")),
                    stringValue(((Map<String, Object>) map).get("email")));
            if (name != null) {
                return name;
            }
        }
        return cleanDisplay(firstNonBlank(
                stringValue(issue.get("assigneeName")),
                stringValue(issue.get("assigneeFullName")),
                stringValue(issue.get("assigneeEmail"))), "Chưa có người phụ trách");
    }

    /**
     * Returns display status for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statuses the statuses parameter
     * @return the display status result
     */
    private String displayStatus(Map<String, Object> issue, Map<String, String> statuses) {
        return cleanDisplay(firstNonBlank(
                stringValue(issue.get("statusName")),
                statuses.get(stringValue(issue.get("statusId")))), "Chưa phân loại");
    }

    /**
     * Returns display priority for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorities the priorities parameter
     * @return the display priority result
     */
    private String displayPriority(Map<String, Object> issue, Map<String, String> priorities) {
        return cleanDisplay(firstNonBlank(
                stringValue(issue.get("priorityName")),
                priorities.get(stringValue(issue.get("priorityId")))), "Chưa phân loại");
    }

    /**
     * Parses project tool data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param raw the raw parameter
     * @return the parse date result
     */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns is my work question for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param question the question parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isMyWorkQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("cua toi") || normalized.contains("cua minh")
                || normalized.contains("viec toi") || normalized.contains("my ");
    }

    /**
     * Returns clean display for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @param fallback the fallback parameter
     * @return the clean display result
     */
    private String cleanDisplay(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replace("|", "/").trim();
    }

    /**
     * Returns first non blank for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param values the values parameter
     * @return the first non blank result
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns string value for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the string value result
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Returns safe for project tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the safe result
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Normalizes project tool content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the normalize result
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT).trim();
        String decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('đ', 'd')
                .replaceAll("\\s+", " ");
    }
}
