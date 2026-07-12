package com.iems.aiservice.service.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProjectIssueToolService {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("\\b([A-Z][A-Z0-9]*-\\d+)\\b");
    private static final ObjectMapper JSON = new ObjectMapper();

    private enum ReportType {
        REPORT_STATS,
        SUMMARY_PROGRESS,
        DAILY_PLAN,
        RISK_ANALYSIS,
        MEMBER_WORKLOAD,
        DEADLINE_CHECK,
        GENERAL_ANALYSIS
    }

    private final RestClient restClient;
    private final ProjectApiClient projectApiClient;

    /**
     * Creates a new project issue tool service with the configured project service base URL.
     *
     * @param baseUrl the project service base URL
     */
    @Autowired
    public ProjectIssueToolService(
            @Value("${ai.agent.project-base-url:http://localhost:8080/project-service}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build(), new ProjectApiClient(baseUrl));
    }

    /**
     * Creates a new project issue tool service instance.
     *
     * @param restClient the rest client parameter
     * @param projectApiClient the project api client parameter
     */
    ProjectIssueToolService(RestClient restClient, ProjectApiClient projectApiClient) {
        this.restClient = restClient;
        this.projectApiClient = projectApiClient;
    }

    /**
     * Handles the project issue tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the handle issue query result
     */
    public String handleIssueQuery(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return "Mình cần biết dự án hiện tại để xem danh sách issue.";
        }

        String normalized = normalize(question);
        boolean myOnly = hasAnyPhrase(normalized, "my", "cua toi", "cua minh", "viec cua toi");
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, myOnly);
        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> typeById = getIssueTypes(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        LocalDate today = LocalDate.now();
        ReportType reportType = detectReportType(normalized);
        if (reportType == ReportType.REPORT_STATS) {
            return buildIssueStatisticsReport(issues, priorityById, statusById);
        }
        boolean needImportanceReason = hasAnyPhrase(normalized,
                "quan trong", "priority", "uu tien", "important", "quan trng", "quan trnog");

        List<Map<String, Object>> filtered = new ArrayList<>(issues);

        if (hasAnyPhrase(normalized, "bug", "loi", "error")) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String typeName = typeById.get(stringValue(issue.get("issueTypeId")));
                        if (typeName == null) {
                            return false;
                        }
                        String n = normalize(typeName);
                        return n.contains("bug") || n.contains("loi");
                    })
                    .collect(Collectors.toList());
        }

        if (needImportanceReason) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
                        return !isDoneStatus(normalize(statusName));
                    })
                    .collect(Collectors.toList());

            filtered.sort(Comparator.comparingInt(
                    issue -> -issueImportanceScore(issue, priorityById, statusById, today)));
        }

        if (hasAnyPhrase(normalized, "hom nay", "today", "hnay")) {
            filtered = filtered.stream()
                    .filter(issue -> {
                        String dueDateRaw = stringValue(issue.get("dueDate"));
                        if (dueDateRaw == null || dueDateRaw.isBlank()) {
                            return false;
                        }
                        try {
                            LocalDate due = LocalDate.parse(dueDateRaw);
                            return !due.isAfter(today);
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            filtered.sort(Comparator.comparing(issue -> LocalDate.parse(stringValue(issue.get("dueDate")))));
        }

        if (filtered.isEmpty()) {
            return "Mình chưa tìm thấy issue phù hợp với yêu cầu này.";
        }

        int limit = Math.min(filtered.size(), 7);
        StringBuilder response = new StringBuilder();
        if (needImportanceReason) {
            response.append("Các việc nên ưu tiên:\n");
        } else {
            response.append("Mình tìm thấy ").append(filtered.size()).append(" issue phù hợp. Hiển thị ")
                    .append(limit).append(" issue quan trọng nhất:\n");
        }

        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = filtered.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));

            if (needImportanceReason) {
                String reason = buildImportantReason(issue, priorityById, statusById, today);
                response.append(i + 1).append(". ")
                        .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason));
            } else {
                response.append(i + 1).append(". ")
                        .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, null));
            }
            response.append("\n");
        }
        appendRemainingIssueNote(response, filtered.size(), limit);

        return response.toString().trim();
    }

    /**
     * Handles the project issue tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the handle issue action result
     */
    public String handleIssueAction(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return structuredError("Mình cần biết dự án hiện tại trước khi cập nhật issue.");
        }

        if (extractIssueKeys(question).isEmpty()) {
            return structuredError("Bạn muốn cập nhật issue nào? Hãy gửi rõ issue key, ví dụ: \"Chuyển IEMS2-5 sang Done\".");
        }

        String normalizedQuestion = normalize(question);
        if (hasAnyPhrase(normalizedQuestion, "gan", "assign", "assignee", "nguoi phu trach")) {
            return buildConfirmation("assign_issue", "Xác nhận gán người phụ trách",
                    "Mình sẽ chỉ thực hiện sau khi bạn xác nhận.",
                    Map.of("issueKey", extractIssueKeys(question).getFirst(), "instruction", question));
        }
        if (hasAnyPhrase(normalizedQuestion, "priority", "do uu tien", "muc uu tien")) {
            return buildConfirmation("update_issue_priority", "Xác nhận cập nhật priority",
                    "Mình sẽ chỉ thực hiện sau khi bạn xác nhận.",
                    Map.of("issueKey", extractIssueKeys(question).getFirst(), "instruction", question));
        }

        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, false);
        if (issues.isEmpty()) {
            return structuredError("Mình chưa thấy issue nào trong dự án để cập nhật.");
        }

        Map<String, String> statuses = getWorkflowStatuses(projectId, authorization);
        if (statuses.isEmpty()) {
            return structuredError("Mình chưa lấy được danh sách trạng thái của dự án. Bạn thử lại sau vài giây nhé.");
        }

        String targetStatusPhrase = extractTargetStatus(question);
        Map.Entry<String, String> matchedStatus = findStatusByPhrase(statuses, targetStatusPhrase);
        if (matchedStatus == null) {
            return structuredError("Bạn muốn chuyển issue sang trạng thái nào? Hãy nói rõ, ví dụ: \"sang In Progress\" hoặc \"sang Done\".");
        }

        List<Map<String, Object>> targets = resolveTargets(question, issues, projectId, authorization);
        if (targets.isEmpty()) {
            return structuredError("Mình không tìm thấy issue key bạn vừa nêu trong dự án này. Bạn kiểm tra lại giúp mình nhé.");
        }
        List<Map<String, Object>> issueCards = targets.stream()
                .limit(5)
                .map(issue -> issueActionPayload(issue, statuses))
                .collect(Collectors.toList());
        return buildConfirmation("update_issue_status", "Xác nhận đổi trạng thái issue",
                "Bạn xác nhận chuyển " + issueCards.size() + " issue sang \"" + matchedStatus.getValue()
                        + "\"? Mình chưa thực hiện thay đổi nào.",
                Map.of(
                        "targetStatusId", matchedStatus.getKey(),
                        "targetStatus", matchedStatus.getValue(),
                        "issues", issueCards));
    }

    /**
     * Handles the project issue tool operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the handle issue analysis result
     */
    public String handleIssueAnalysis(String question, String projectId, String authorization) {
        if (projectId == null || projectId.isBlank()) {
            return "Mình cần biết dự án hiện tại để phân tích công việc.";
        }

        String normalized = normalize(question);
        boolean myOnly = hasAnyPhrase(normalized, "my", "cua toi", "cua minh", "viec cua toi");
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, myOnly);
        if (issues.isEmpty()) {
            return "Mình chưa thấy issue nào để phân tích trong dự án này.";
        }

        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        Map<String, String> typeById = getIssueTypes(projectId, authorization);

        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> issueById = new HashMap<>();
        Map<String, Integer> childCountByParentId = new HashMap<>();
        List<Map<String, Object>> openIssues = new ArrayList<>();

        for (Map<String, Object> issue : issues) {
            String id = stringValue(issue.get("id"));
            if (id != null) {
                issueById.put(id, issue);
            }

            String parentId = stringValue(issue.get("parentId"));
            if (parentId != null && !parentId.isBlank()) {
                childCountByParentId.put(parentId, childCountByParentId.getOrDefault(parentId, 0) + 1);
            }

            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            if (!isDoneStatus(normalize(statusName))) {
                openIssues.add(issue);
            }
        }

        List<Map<String, Object>> taskTargets = selectTaskTargets(question, issues, openIssues,
                priorityById, statusById, today);
        if (taskTargets.isEmpty()) {
            return "Mình chưa tìm thấy task phù hợp để phân tích.";
        }

        ReportType reportType = detectReportType(normalized);

        if (reportType == ReportType.SUMMARY_PROGRESS) {
            return buildStandupBrief(issues, openIssues, priorityById, statusById, today);
        }

        if (reportType == ReportType.DAILY_PLAN) {
            return buildDailyPlan(taskTargets, priorityById, statusById, today);
        }

        if (reportType == ReportType.RISK_ANALYSIS) {
            return buildProjectRiskReview(openIssues, priorityById, statusById, today);
        }

        if (reportType == ReportType.MEMBER_WORKLOAD) {
            return buildMemberWorkloadReport(issues, priorityById, statusById, today);
        }

        if (reportType == ReportType.DEADLINE_CHECK) {
            return buildDeadlineCheck(openIssues, priorityById, statusById, today);
        }

        if (reportType == ReportType.REPORT_STATS) {
            return buildIssueStatisticsReport(issues, priorityById, statusById);
        }

        if (hasAnyPhrase(normalized, "grooming", "lam ro", "thieu mo ta", "acceptance", "test case",
                "task mo ho", "issue mo ho", "chat luong task")) {
            return buildIssueQualityAudit(openIssues, priorityById, statusById, today);
        }

        boolean concisePriorityMode = hasAnyPhrase(normalized,
                "de xuat uu tien",
                "uu tien truoc",
                "phan tich cong viec hien tai",
                "phan tich uu tien",
                "uu tien cong viec");

        if (concisePriorityMode) {
            return buildConcisePriorityAnalysis(taskTargets, typeById, priorityById, statusById, today);
        }

        StringBuilder result = new StringBuilder();
        result.append("Mình đã phân tích nhanh các task ")
                .append(myOnly ? "của bạn" : "trong dự án")
                .append(". Mỗi task bên dưới đều có hướng làm ngắn gọn, làm được ngay:\n\n");

        int limit = Math.min(taskTargets.size(), 4);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = taskTargets.get(i);
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            Integer storyPoints = parseIntValue(issue.get("storyPoints"));
            String reason = buildShortPriorityReason(issue, priorityName, storyPoints, statusById, today);
            result.append(i + 1).append(". ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n");
        }
        result.append("\n---\n\n## Phan tich chi tiet\n\n");

        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = taskTargets.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            LocalDate due = parseDate(stringValue(issue.get("dueDate")));
            Integer storyPoints = parseIntValue(issue.get("storyPoints"));

            if (i > 0) {
                result.append("\n---\n\n");
            }

            result.append("### ")
                    .append(key == null ? "(no-key)" : key)
                    .append(" - ")
                    .append(title == null ? "(no-title)" : title)
                    .append("\n\n")
                    .append(buildIssueTemplate(
                            issue,
                            typeName,
                            priorityName,
                            statusName,
                            due,
                            storyPoints,
                            today,
                            issueById,
                            childCountByParentId,
                            priorityById,
                            statusById,
                            typeById));
        }

        return result.toString().trim();
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get project overview result
     */
    public Map<String, Object> getProjectOverview(String projectId, String authorization) {
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, false);
        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        LocalDate today = LocalDate.now();
        return Map.of(
                "totalIssues", issues.size(),
                "issues", normalizeIssuesForPrompt(issues, priorityById, statusById, today));
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get project issues result
     */
    public List<Map<String, Object>> getProjectIssues(String projectId, String authorization) {
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, false);
        return normalizeIssuesForPrompt(issues, getIssuePriorities(projectId, authorization),
                getWorkflowStatuses(projectId, authorization), LocalDate.now());
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get my project issues result
     */
    public List<Map<String, Object>> getMyProjectIssues(String projectId, String authorization) {
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, true);
        return normalizeIssuesForPrompt(issues, getIssuePriorities(projectId, authorization),
                getWorkflowStatuses(projectId, authorization), LocalDate.now());
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get sprint status result
     */
    public Map<String, Object> getSprintStatus(String projectId, String authorization) {
        return getProjectOverview(projectId, authorization);
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get member workload result
     */
    public Map<String, Object> getMemberWorkload(String projectId, String authorization) {
        List<Map<String, Object>> issues = getProjectIssues(projectId, authorization);
        Map<String, Long> counts = issues.stream()
                .collect(Collectors.groupingBy(issue -> stringValue(issue.get("assigneeName")), LinkedHashMap::new,
                        Collectors.counting()));
        return Map.of("members", counts.entrySet().stream()
                .map(e -> Map.of("name", e.getKey(), "openIssues", e.getValue()))
                .toList());
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get risk signals result
     */
    public List<Map<String, Object>> getRiskSignals(String projectId, String authorization) {
        List<Map<String, Object>> issues = getScopedIssues(projectId, authorization, false);
        Map<String, String> priorityById = getIssuePriorities(projectId, authorization);
        Map<String, String> statusById = getWorkflowStatuses(projectId, authorization);
        LocalDate today = LocalDate.now();
        return normalizeIssuesForPrompt(issues.stream()
                .filter(issue -> isBlockedIssue(issue, statusById) || isOverdue(issue, today) || isDueSoon(issue, today))
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(7)
                .toList(), priorityById, statusById, today);
    }

    /**
     * Updates project issue tool data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the update issue status result
     */
    public String updateIssueStatus(String question, String projectId, String authorization) {
        return handleIssueAction(question, projectId, authorization);
    }

    /**
     * Assigns project issue tool data according to the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the assign issue result
     */
    public String assignIssue(String question, String projectId, String authorization) {
        return handleIssueAction(question, projectId, authorization);
    }

    /**
     * Creates project issue tool data for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the create issue result
     */
    public String createIssue(String question, String projectId, String authorization) {
        return buildConfirmation("create_issue", "Xác nhận tạo issue",
                "Mình sẽ chỉ tạo issue sau khi bạn xác nhận.", Map.of("instruction", question));
    }

    /**
     * Searches project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the search project documents result
     */
    public List<Map<String, Object>> searchProjectDocuments(String question, String projectId, String authorization) {
        return List.of();
    }

    /**
     * Resolves project issue tool information for the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param issues the issues parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the resolve targets result
     */
    private List<Map<String, Object>> resolveTargets(String question,
            List<Map<String, Object>> issues,
            String projectId,
            String authorization) {
        List<String> keys = extractIssueKeys(question);
        if (!keys.isEmpty()) {
            Map<String, Map<String, Object>> byKey = new HashMap<>();
            for (Map<String, Object> issue : issues) {
                String key = stringValue(issue.get("issueKey"));
                if (key != null) {
                    byKey.put(normalize(key), issue);
                }
            }

            List<Map<String, Object>> targetByKey = new ArrayList<>();
            for (String key : keys) {
                Map<String, Object> found = byKey.get(normalize(key));
                if (found != null) {
                    targetByKey.add(found);
                }
            }
            return targetByKey;
        }

        String normalized = normalize(question);
        if (normalized.contains("dong loat") || normalized.contains("batch") || normalized.contains("tat ca")) {
            if (hasAnyPhrase(normalized, "bug", "loi", "error")) {
                Map<String, String> typeById = getIssueTypes(projectId, authorization);
                return issues.stream()
                        .filter(issue -> {
                            String typeName = typeById.get(stringValue(issue.get("issueTypeId")));
                            String n = normalize(typeName);
                            return n.contains("bug") || n.contains("loi");
                        })
                        .collect(Collectors.toList());
            }
            return issues;
        }

        return List.of();
    }

    /**
     * Performs change issue status for project issue tool processing.
     *
     * @param projectId the project id parameter
     * @param issueId the issue id parameter
     * @param statusId the status id parameter
     * @param authorization the authorization parameter
     */
    private void changeIssueStatus(String projectId, String issueId, String statusId, String authorization) {
        restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/projects/{projectId}/issues/{issueId}/status")
                        .queryParam("statusId", statusId)
                        .build(projectId, issueId))
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    /**
     * Returns issue action payload for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statusById the status by id parameter
     * @return the issue action payload result
     */
    private Map<String, Object> issueActionPayload(Map<String, Object> issue, Map<String, String> statusById) {
        String statusId = stringValue(issue.get("statusId"));
        return Map.of(
                "issueId", cardValue(stringValue(issue.get("id")), ""),
                "issueKey", cardValue(stringValue(issue.get("issueKey")), "Issue chua co key"),
                "title", cardValue(stringValue(issue.get("title")), "Chua co tieu de"),
                "currentStatusId", cardValue(statusId, ""),
                "currentStatus", cleanDisplay(statusById.get(statusId), "Chua phan loai"));
    }

    /**
     * Finds project issue tool information that matches the request.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param statusById the status by id parameter
     * @param phrase the phrase parameter
     * @return the find status by phrase result
     */
    private Map.Entry<String, String> findStatusByPhrase(Map<String, String> statusById, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }

        String target = normalize(phrase);
        for (Map.Entry<String, String> entry : statusById.entrySet()) {
            String name = normalize(entry.getValue());
            if (name.equals(target) || name.contains(target) || target.contains(name)) {
                return entry;
            }
        }

        if (target.contains("in progress") || target.contains("dang lam")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("in progress") || name.contains("dang lam") || name.contains("doing")) {
                    return entry;
                }
            }
        }

        if (target.contains("done") || target.contains("hoan thanh")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("done") || name.contains("hoan thanh") || name.contains("closed")) {
                    return entry;
                }
            }
        }

        if (target.contains("todo") || target.contains("to do") || target.contains("chua lam")) {
            for (Map.Entry<String, String> entry : statusById.entrySet()) {
                String name = normalize(entry.getValue());
                if (name.contains("todo") || name.contains("to do") || name.contains("chua lam")
                        || name.contains("open")) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Returns extract target status for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param question the question parameter
     * @return the extract target status result
     */
    private String extractTargetStatus(String question) {
        String normalized = normalize(question);
        if (normalized.contains("in progress") || normalized.contains("dang lam") || normalized.contains("doing")) {
            return "in progress";
        }
        if (normalized.contains("done") || normalized.contains("hoan thanh") || normalized.contains("closed")) {
            return "done";
        }
        if (normalized.contains("todo") || normalized.contains("to do") || normalized.contains("chua lam")
                || normalized.contains("open")) {
            return "todo";
        }

        Pattern p = Pattern.compile("(?:sang|to)\\s+([\\p{L}\\s-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(question);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    /**
     * Returns extract issue keys for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param text the text parameter
     * @return the matching result collection
     */
    private List<String> extractIssueKeys(String text) {
        List<String> keys = new ArrayList<>();
        Matcher m = ISSUE_KEY_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @param myIssues the my issues parameter
     * @return the get scoped issues result
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getScopedIssues(String projectId, String authorization, boolean myIssues) {
        List<Map<String, Object>> projectIssues = filterIssuesToProject(
                getIssues(projectId, authorization, false),
                projectId);

        if (!myIssues) {
            return projectIssues;
        }

        List<Map<String, Object>> myIssueCandidates = filterIssuesToProject(
                getIssues(projectId, authorization, true),
                projectId);

        if (projectIssues.isEmpty()) {
            return myIssueCandidates;
        }

        Set<String> projectIssueIds = new HashSet<>();
        Set<String> projectIssueKeys = new HashSet<>();
        for (Map<String, Object> issue : projectIssues) {
            String id = stringValue(issue.get("id"));
            String key = stringValue(issue.get("issueKey"));
            if (id != null && !id.isBlank()) {
                projectIssueIds.add(id);
            }
            if (key != null && !key.isBlank()) {
                projectIssueKeys.add(normalize(key));
            }
        }

        return myIssueCandidates.stream()
                .filter(issue -> {
                    String id = stringValue(issue.get("id"));
                    String key = stringValue(issue.get("issueKey"));
                    return (id != null && projectIssueIds.contains(id))
                            || (key != null && projectIssueKeys.contains(normalize(key)));
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns filter issues to project for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param projectId the project id parameter
     * @return the filter issues to project result
     */
    private List<Map<String, Object>> filterIssuesToProject(List<Map<String, Object>> issues, String projectId) {
        if (projectId == null || projectId.isBlank() || issues.isEmpty()) {
            return issues;
        }

        boolean hasProjectMarker = issues.stream().anyMatch(issue -> extractIssueProjectId(issue) != null);
        if (!hasProjectMarker) {
            return issues;
        }

        return issues.stream()
                .filter(issue -> projectId.equals(extractIssueProjectId(issue)))
                .collect(Collectors.toList());
    }

    /**
     * Returns extract issue project id for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return the extract issue project id result
     */
    @SuppressWarnings("unchecked")
    private String extractIssueProjectId(Map<String, Object> issue) {
        String direct = firstNonBlank(
                stringValue(issue.get("projectId")),
                stringValue(issue.get("project_id")),
                stringValue(issue.get("projectID")));
        if (direct != null) {
            return direct;
        }

        Object project = issue.get("project");
        if (project instanceof Map<?, ?> projectMap) {
            return firstNonBlank(
                    stringValue(((Map<String, Object>) projectMap).get("id")),
                    stringValue(((Map<String, Object>) projectMap).get("projectId")));
        }

        return null;
    }

    /**
     * Returns first non blank for project issue tool processing.
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
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @param myIssues the my issues parameter
     * @return the get issues result
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getIssues(String projectId, String authorization, boolean myIssues) {
        List<Map<String, Object>> result = new ArrayList<>();
        mergeIssues(result, myIssues
                ? projectApiClient.listMyIssues(projectId, authorization)
                : projectApiClient.listProjectIssues(projectId, authorization));

        if (!myIssues) {
            mergeIssues(result, projectApiClient.listProjectIssuesPaged(projectId, authorization, 200));
        }

        return result;
    }

    /**
     * Returns read issue list for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param path the path parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the read issue list result
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readIssueList(String path, String projectId, String authorization) {
        Map<String, Object> response = restClient.get()
                .uri(path, projectId)
                .header("Authorization", authorization)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            return List.of();
        }
        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    /**
     * Returns read paged issues for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the read paged issues result
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readPagedIssues(String projectId, String authorization) {
        List<Map<String, Object>> result = new ArrayList<>();
        int page = 0;
        int totalPages = 1;
        do {
            int currentPage = page;
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{projectId}/issues/paged")
                            .queryParam("page", currentPage)
                            .queryParam("size", 200)
                            .build(projectId))
                    .header("Authorization", authorization)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("data") instanceof Map<?, ?> dataMapRaw)) {
                break;
            }
            Map<String, Object> data = (Map<String, Object>) dataMapRaw;
            Object content = firstNonNull(data.get("content"), data.get("items"), data.get("data"));
            if (content instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add(new LinkedHashMap<>((Map<String, Object>) map));
                    }
                }
            }
            totalPages = parseIntValue(data.get("totalPages")) == null ? 1 : parseIntValue(data.get("totalPages"));
            page++;
        } while (page < totalPages && page < 20);

        return result;
    }

    /**
     * Performs merge issues for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param target the target parameter
     * @param source the source parameter
     */
    private void mergeIssues(List<Map<String, Object>> target, List<Map<String, Object>> source) {
        Set<String> seen = target.stream()
                .map(this::issueIdentity)
                .collect(Collectors.toCollection(HashSet::new));
        for (Map<String, Object> issue : source) {
            String identity = issueIdentity(issue);
            if (seen.add(identity)) {
                target.add(issue);
            }
        }
    }

    /**
     * Returns issue identity for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return the issue identity result
     */
    private String issueIdentity(Map<String, Object> issue) {
        String id = firstNonBlank(stringValue(issue.get("id")), stringValue(issue.get("issueId")));
        if (id != null) {
            return "id:" + id;
        }
        String key = stringValue(issue.get("issueKey"));
        return key == null ? "object:" + System.identityHashCode(issue) : "key:" + normalize(key);
    }

    /**
     * Returns first non null for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param values the values parameter
     * @return the first non null result
     */
    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get issue types result
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getIssueTypes(String projectId, String authorization) {
        return mapIdToName(projectApiClient.listIssueTypes(projectId, authorization));
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get issue priorities result
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getIssuePriorities(String projectId, String authorization) {
        return mapIdToName(projectApiClient.listIssuePriorities(projectId, authorization));
    }

    /**
     * Retrieves project issue tool information.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @return the get workflow statuses result
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getWorkflowStatuses(String projectId, String authorization) {
        List<Map<String, Object>> workflows = projectApiClient.listWorkflows(projectId, authorization);
        if (workflows.isEmpty()) {
            return Map.of();
        }

        String workflowId = null;
        for (Map<String, Object> workflow : workflows) {
            Object isDefault = workflow.get("isDefault");
            if (Boolean.TRUE.equals(isDefault)) {
                workflowId = stringValue(workflow.get("id"));
                break;
            }
        }
        if (workflowId == null) {
            workflowId = stringValue(workflows.get(0).get("id"));
        }
        if (workflowId == null || workflowId.isBlank()) {
            return Map.of();
        }

        return mapIdToName(projectApiClient.listWorkflowStatuses(projectId, workflowId, authorization));
    }

    /**
     * Maps project issue tool data to the target representation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param response the response parameter
     * @return the map id to name from data array result
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> mapIdToNameFromDataArray(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return Map.of();
        }

        Map<String, String> byId = new LinkedHashMap<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String id = stringValue(map.get("id"));
                String name = stringValue(map.get("name"));
                if (id != null && name != null) {
                    byId.put(id, name);
                }
            }
        }
        return byId;
    }

    /**
     * Maps project issue tool data to the target representation.
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
        for (Map<String, Object> item : items) {
            String id = stringValue(item.get("id"));
            String name = stringValue(item.get("name"));
            if (id != null && name != null) {
                byId.put(id, name);
            }
        }
        return byId;
    }

    /**
     * Returns priority weight for project issue tool processing.
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
        String normalized = normalize(priorityName);
        if (normalized.contains("critical") || normalized.contains("urgent") || normalized.contains("khan")
                || normalized.contains("cao")) {
            return 4;
        }
        if (normalized.contains("high")) {
            return 3;
        }
        if (normalized.contains("medium") || normalized.contains("trung")) {
            return 2;
        }
        if (normalized.contains("low") || normalized.contains("thap")) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns safe message for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param ex the ex parameter
     * @return the safe message result
     */
    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? "request failed" : msg;
    }

    /**
     * Parses project issue tool data.
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
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Returns is done status for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param normalizedStatus the normalized status parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isDoneStatus(String normalizedStatus) {
        return normalizedStatus.contains("done")
                || normalizedStatus.contains("hoan thanh")
                || normalizedStatus.contains("closed")
                || normalizedStatus.contains("resolved");
    }

    /**
     * Returns issue importance score for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the issue importance score result
     */
    private int issueImportanceScore(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        int score = priorityWeight(priorityName) * 10;

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            score += 15;
        }
        if (status.contains("progress") || status.contains("dang lam") || status.contains("doing")) {
            score += 4;
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null) {
            if (due.isBefore(today)) {
                score += 20;
            } else if (due.isEqual(today)) {
                score += 12;
            } else if (due.minusDays(2).isBefore(today)) {
                score += 6;
            }
        }

        return score;
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build important reason result
     */
    private String buildImportantReason(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();

        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        int weight = priorityWeight(priorityName);
        if (weight >= 4) {
            reasons.add("priority rat cao");
        } else if (weight == 3) {
            reasons.add("priority cao");
        }

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            reasons.add("dang bi blocker/stuck");
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null) {
            if (due.isBefore(today)) {
                reasons.add("da qua han");
            } else if (due.isEqual(today)) {
                reasons.add("den han hom nay");
            } else if (due.minusDays(2).isBefore(today)) {
                reasons.add("sap den han");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("anh huong truc tiep den tien do");
        }

        return String.join(", ", reasons);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Remove or clear the requested domain data when allowed.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build task action result
     */
    private String buildTaskAction(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        int weight = priorityWeight(priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown"));

        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            return "go blocker ngay, gan owner va deadline clear blocker";
        }
        if (due != null && due.isBefore(today)) {
            return "escalate va cap nhat status trong ngay de xu ly no qua han";
        }
        if (due != null && due.isEqual(today)) {
            return "uu tien day task nay len dau ngay va chot ket qua truoc EOD";
        }
        if (weight >= 3) {
            return "dua vao nhom focus, cap nhat tien do 2 lan/ngay";
        }
        return "tiep tuc theo sprint plan va cap nhat tien do deu";
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param openIssues the open issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build standup brief result
     */
    private String buildStandupBrief(List<Map<String, Object>> issues,
            List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> focus = openIssues.stream()
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(5)
                .collect(Collectors.toList());

        long doneCount = issues.stream()
                .filter(issue -> isDoneStatus(normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""))))
                .count();
        long blockedCount = openIssues.stream().filter(issue -> isBlockedIssue(issue, statusById)).count();
        long overdueCount = openIssues.stream().filter(issue -> isOverdue(issue, today)).count();
        long inProgressCount = openIssues.stream().filter(issue -> isInProgressIssue(issue, statusById)).count();

        Map<String, Long> statusCounts = countByDisplayName(issues, statusById, "statusId", "Chưa phân loại");
        StringBuilder out = new StringBuilder();
        out.append("## Tổng quan\n")
                .append("- Tổng số issue: ").append(issues.size()).append("\n")
                .append("- Đang mở: ").append(openIssues.size()).append("\n")
                .append("- Đang làm: ").append(inProgressCount).append("\n")
                .append("- Đã xong: ").append(doneCount).append("\n")
                .append("- Quá hạn/rủi ro: ").append(overdueCount + blockedCount).append("\n\n");

        out.append("## Thống kê theo trạng thái\n\n")
                .append("| Trạng thái | Số issue |\n")
                .append("|---|---:|\n");
        statusCounts.forEach((status, count) -> out.append("| ").append(status).append(" | ").append(count).append(" |\n"));

        out.append("\n## Việc đang làm\n");
        appendIssueLines(out, focus.stream()
                .filter(issue -> isInProgressIssue(issue, statusById))
                .limit(5)
                .collect(Collectors.toList()), priorityById, statusById, today);

        out.append("\n## Việc đã xong\n");
        List<Map<String, Object>> doneIssues = issues.stream()
                .filter(issue -> isDoneStatus(normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""))))
                .limit(5)
                .collect(Collectors.toList());
        appendIssueLines(out, doneIssues, priorityById, statusById, today);

        out.append("\n## Việc quá hạn/rủi ro\n");
        for (int i = 0; i < focus.size(); i++) {
            Map<String, Object> issue = focus.get(i);
            if (!isOverdue(issue, today) && !isBlockedIssue(issue, statusById)) {
                continue;
            }
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            String reason = buildStandupReason(issue, priorityById, statusById, today);
            out.append("- ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n");
        }

        out.append("\n## Nhận xét sức khỏe dự án\n")
                .append(buildProjectHealthComment(openIssues.size(), overdueCount, blockedCount, issues.size()));
        appendRemainingIssueNote(out, openIssues.size(), Math.min(openIssues.size(), 7));

        return structured(Map.of(
                "type", "summary",
                "title", "Tóm tắt sức khỏe dự án",
                "summary", "Dự án hiện có " + issues.size() + " issue, trong đó " + openIssues.size()
                        + " issue đang mở.",
                "metrics", List.of(
                        metric("Tổng issue", issues.size()),
                        metric("Đang mở", openIssues.size()),
                        metric("Đã xong", doneCount),
                        metric("Quá hạn", overdueCount),
                        metric("Blocker", blockedCount)),
                "sections", List.of(
                        section("Tổng quan", List.of("Tổng số issue: " + issues.size(),
                                "Đang mở: " + openIssues.size(), "Đang làm: " + inProgressCount,
                                "Đã xong: " + doneCount)),
                        section("Thống kê theo trạng thái", statusCounts.entrySet().stream()
                                .map(e -> e.getKey() + ": " + e.getValue()).toList()),
                        section("Việc đang làm", issueSummaries(focus.stream()
                                .filter(issue -> isInProgressIssue(issue, statusById)).limit(5).toList(),
                                priorityById, statusById, today)),
                        section("Việc đã xong", issueSummaries(doneIssues, priorityById, statusById, today)),
                        section("Việc quá hạn/rủi ro", issueSummaries(focus.stream()
                                .filter(issue -> isOverdue(issue, today) || isBlockedIssue(issue, statusById))
                                .limit(7).toList(), priorityById, statusById, today)),
                        section("Nhận xét sức khỏe dự án",
                                List.of(buildProjectHealthComment(openIssues.size(), overdueCount, blockedCount, issues.size())))),
                "issues", normalizeIssuesForPrompt(focus, priorityById, statusById, today)));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param openIssues the open issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build project risk review result
     */
    private String buildProjectRiskReview(List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> riskIssues = openIssues.stream()
                .filter(issue -> isBlockedIssue(issue, statusById)
                        || isOverdue(issue, today)
                        || isDueSoon(issue, today)
                        || priorityWeight(priorityById.getOrDefault(stringValue(issue.get("priorityId")), "")) >= 3)
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(6)
                .collect(Collectors.toList());

        if (riskIssues.isEmpty()) {
            return structured(Map.of(
                    "type", "risk_analysis",
                    "title", "Phân tích rủi ro",
                    "summary", "Mức rủi ro chung: Thấp. Chưa thấy rủi ro lớn trong các issue đang mở.",
                    "metrics", List.of(metric("Mức rủi ro", "Thấp")),
                    "sections", List.of(section("Hành động đề xuất",
                            List.of("Kiểm tra các task thiếu hạn chót hoặc thiếu mô tả trước sprint review."))),
                    "issues", List.of(),
                    "actions", List.of("Tiếp tục theo dõi issue gần hạn.")));
        }

        StringBuilder out = new StringBuilder();
        long criticalSignals = riskIssues.stream().filter(issue -> isBlockedIssue(issue, statusById) || isOverdue(issue, today)).count();
        String level = criticalSignals >= 3 ? "Cao" : criticalSignals >= 1 ? "Trung bình" : "Thấp";
        out.append("## Mức rủi ro chung: ").append(level).append("\n\n")
                .append("## Rủi ro chính\n");
        for (int i = 0; i < riskIssues.size(); i++) {
            Map<String, Object> issue = riskIssues.get(i);
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            String reason = buildRiskReason(issue, priorityById, statusById, today);
            out.append(i + 1).append(". ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n");
        }

        out.append("\n## Tác động\n")
                .append("- Có thể làm chậm mốc hoàn thành nếu blocker hoặc issue quá hạn không được xử lý trong ngày.\n")
                .append("- Các issue priority cao dễ kéo theo rework nếu thiếu owner hoặc tiêu chí hoàn thành rõ ràng.\n\n")
                .append("## Hành động đề xuất\n")
                .append("- Gỡ blocker trước, chốt owner và thời hạn xử lý rõ ràng.\n")
                .append("- Với issue quá hạn, cập nhật ETA mới và cắt scope nếu cần.\n")
                .append("- Với issue priority cao, chia nhỏ việc và xác nhận acceptance criteria trước khi triển khai.\n");

        return structured(Map.of(
                "type", "risk_analysis",
                "title", "Phân tích rủi ro",
                "summary", "Mức rủi ro chung: " + level,
                "metrics", List.of(metric("Mức rủi ro", level), metric("Issue rủi ro", riskIssues.size())),
                "sections", List.of(
                        section("Rủi ro chính", issueSummaries(riskIssues, priorityById, statusById, today)),
                        section("Tác động", List.of(
                                "Có thể làm chậm mốc hoàn thành nếu blocker hoặc issue quá hạn không được xử lý trong ngày.",
                                "Các issue priority cao dễ kéo theo rework nếu thiếu owner hoặc tiêu chí hoàn thành rõ ràng.")),
                        section("Hành động đề xuất", List.of(
                                "Gỡ blocker trước, chốt owner và thời hạn xử lý rõ ràng.",
                                "Với issue quá hạn, cập nhật ETA mới và cắt scope nếu cần.",
                                "Với issue priority cao, chia nhỏ việc và xác nhận acceptance criteria trước khi triển khai."))),
                "issues", normalizeIssuesForPrompt(riskIssues, priorityById, statusById, today),
                "actions", List.of("Gỡ blocker", "Cập nhật ETA", "Chia nhỏ issue priority cao")));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param openIssues the open issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build issue quality audit result
     */
    private String buildIssueQualityAudit(List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> weakIssues = openIssues.stream()
                .filter(issue -> !hasUsefulDescription(issue)
                        || !hasAssignee(issue)
                        || stringValue(issue.get("dueDate")) == null
                        || parseIntValue(issue.get("storyPoints")) == null)
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(8)
                .collect(Collectors.toList());

        if (weakIssues.isEmpty()) {
            return "Cac task dang mo nhin kha day du: co mo ta/assignee/due/story point co ban. Nen chuyen sang risk review hoac daily plan.";
        }

        StringBuilder out = new StringBuilder();
        out.append("Nhung task can grooming/lam ro:\n");
        for (int i = 0; i < weakIssues.size(); i++) {
            Map<String, Object> issue = weakIssues.get(i);
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            String reason = buildQualityReason(issue);
            out.append(i + 1).append(". ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n");
        }

        out.append("\n---\n\n")
                .append("## Checklist grooming nen bo sung\n\n")
                .append("- Muc tieu: task nay giai quyet van de gi?\n")
                .append("- Acceptance criteria: dieu kien nao thi duoc xem la done?\n")
                .append("- Scope/khong scope: lam gi va khong lam gi?\n")
                .append("- Test case: happy path, invalid input, permission, regression.\n")
                .append("- Owner/deadline: ai chiu trach nhiem va khi nao can xong?\n");

        return out.toString().trim();
    }

    /**
     * Returns select task targets for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param question the question parameter
     * @param allIssues the all issues parameter
     * @param openIssues the open issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the select task targets result
     */
    private List<Map<String, Object>> selectTaskTargets(String question,
            List<Map<String, Object>> allIssues,
            List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> requestedKeys = extractIssueKeys(question);
        if (!requestedKeys.isEmpty()) {
            Map<String, Map<String, Object>> byKey = new HashMap<>();
            for (Map<String, Object> issue : allIssues) {
                String key = stringValue(issue.get("issueKey"));
                if (key != null) {
                    byKey.put(normalize(key), issue);
                }
            }

            List<Map<String, Object>> selected = new ArrayList<>();
            for (String key : requestedKeys) {
                Map<String, Object> found = byKey.get(normalize(key));
                if (found != null) {
                    selected.add(found);
                }
            }
            return selected;
        }

        return openIssues.stream()
                .sorted(Comparator.comparingInt(
                        issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(7)
                .collect(Collectors.toList());
    }

    /**
     * Returns is blocked issue for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statusById the status by id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isBlockedIssue(Map<String, Object> issue, Map<String, String> statusById) {
        String status = normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""));
        return status.contains("block") || status.contains("stuck") || status.contains("hold") || status.contains("ket");
    }

    /**
     * Returns is in progress issue for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statusById the status by id parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isInProgressIssue(Map<String, Object> issue, Map<String, String> statusById) {
        String status = normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""));
        return status.contains("progress") || status.contains("doing") || status.contains("dang lam");
    }

    /**
     * Returns is overdue for project issue tool processing.
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
     * Returns is due soon for project issue tool processing.
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
    private boolean isDueSoon(Map<String, Object> issue, LocalDate today) {
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        return due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(3));
    }

    /**
     * Returns has useful description for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean hasUsefulDescription(Map<String, Object> issue) {
        String description = stripHtml(stringValue(issue.get("description")));
        return description != null && description.length() >= 40;
    }

    /**
     * Returns has assignee for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean hasAssignee(Map<String, Object> issue) {
        return firstNonBlank(
                stringValue(issue.get("assigneeId")),
                stringValue(issue.get("assignee_id")),
                stringValue(issue.get("assigneeUserId")),
                stringValue(issue.get("assigneeName")),
                stringValue(issue.get("assignee"))) != null;
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build standup reason result
     */
    private String buildStandupReason(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        if (isBlockedIssue(issue, statusById)) {
            return "dang blocker/stuck, can noi trong standup de xin ho tro";
        }
        if (isOverdue(issue, today)) {
            return "da qua han, can cap nhat ETA va cach xu ly";
        }
        if (isDueSoon(issue, today)) {
            return "gan deadline, nen day len focus hom nay";
        }
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        if (priorityWeight(priorityName) >= 3) {
            return "priority cao, anh huong tien do neu tre";
        }
        return "nam trong nhom viec nen tiep tuc theo sprint plan";
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build risk reason result
     */
    private String buildRiskReason(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();
        if (isBlockedIssue(issue, statusById)) {
            reasons.add("blocker/stuck");
        }
        if (isOverdue(issue, today)) {
            reasons.add("qua han");
        } else if (isDueSoon(issue, today)) {
            reasons.add("gan deadline");
        }
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        if (priorityWeight(priorityName) >= 3) {
            reasons.add("priority cao");
        }
        if (!hasUsefulDescription(issue)) {
            reasons.add("mo ta thieu, de rework");
        }
        return reasons.isEmpty() ? "can theo doi vi co anh huong den tien do" : String.join(", ", reasons);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return the build quality reason result
     */
    private String buildQualityReason(Map<String, Object> issue) {
        List<String> reasons = new ArrayList<>();
        if (!hasUsefulDescription(issue)) {
            reasons.add("thieu mo ta/acceptance criteria");
        }
        if (!hasAssignee(issue)) {
            reasons.add("chua ro owner");
        }
        if (stringValue(issue.get("dueDate")) == null) {
            reasons.add("chua co deadline");
        }
        if (parseIntValue(issue.get("storyPoints")) == null) {
            reasons.add("chua estimate story point");
        }
        return reasons.isEmpty() ? "can grooming them" : String.join(", ", reasons);
    }

    /**
     * Returns strip html for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the strip html result
     */
    private String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param storyPoints the story points parameter
     * @param descriptionNormalized the description normalized parameter
     * @param typeName the type name parameter
     * @return the build complexity insight result
     */
    private String buildComplexityInsight(Integer storyPoints, String descriptionNormalized, String typeName) {
        List<String> notes = new ArrayList<>();

        if (storyPoints != null) {
            if (storyPoints >= 8) {
                notes.add("khoi luong lon");
            } else if (storyPoints >= 5) {
                notes.add("khoi luong trung binh-cao");
            } else if (storyPoints >= 1) {
                notes.add("khoi luong nho");
            } else {
                notes.add("khoi luong rat nho");
            }
        } else {
            notes.add("chua co story point, can estimate");
        }

        String nType = normalize(typeName);
        if (nType.contains("bug") || nType.contains("loi")) {
            notes.add("task dang o nhom bug, can uu tien xac dinh impact");
        }

        if (descriptionNormalized == null || descriptionNormalized.isBlank()) {
            notes.add("description thieu thong tin");
        } else {
            if (descriptionNormalized.length() < 40) {
                notes.add("description ngan, de thieu context");
            }
            if (descriptionNormalized.contains("api") || descriptionNormalized.contains("db")
                    || descriptionNormalized.contains("migration") || descriptionNormalized.contains("auth")) {
                notes.add("co yeu to ky thuat can review ky");
            }
        }

        return String.join(", ", notes);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param title the title parameter
     * @param descriptionNormalized the description normalized parameter
     * @param due the due parameter
     * @param statusName the status name parameter
     * @param priorityName the priority name parameter
     * @param today the today parameter
     * @return the build task risk insight result
     */
    private String buildTaskRiskInsight(String title,
            String descriptionNormalized,
            LocalDate due,
            String statusName,
            String priorityName,
            LocalDate today) {
        List<String> risks = new ArrayList<>();
        String nStatus = normalize(statusName);
        String nTitle = normalize(title);
        int pWeight = priorityWeight(priorityName);

        if (due != null && due.isBefore(today)) {
            risks.add("dang qua han");
        } else if (due != null && due.isEqual(today)) {
            risks.add("den han hom nay");
        }

        if (pWeight >= 3) {
            risks.add("priority cao nen anh huong lon neu tre");
        }

        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            risks.add("dang blocker/stuck");
        }

        if (nTitle.contains("fix") || nTitle.contains("bug") || nTitle.contains("error") || nTitle.contains("loi")) {
            risks.add("co dau hieu issue ve chat luong/defect");
        }

        if (descriptionNormalized != null && (descriptionNormalized.contains("security")
                || descriptionNormalized.contains("auth")
                || descriptionNormalized.contains("payment"))) {
            risks.add("lien quan luong nhay cam, can test/regression");
        }

        if (risks.isEmpty()) {
            risks.add("rui ro trung binh, theo doi tien do thuong xuyen");
        }

        return String.join(", ", risks);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param issueById the issue by id parameter
     * @param childCountByParentId the child count by parent id parameter
     * @return the build parent child insight result
     */
    private String buildParentChildInsight(Map<String, Object> issue,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId) {
        String issueId = stringValue(issue.get("id"));
        String parentId = stringValue(issue.get("parentId"));
        List<String> insights = new ArrayList<>();

        if (parentId != null && !parentId.isBlank()) {
            Map<String, Object> parent = issueById.get(parentId);
            String parentKey = parent == null ? parentId : stringValue(parent.get("issueKey"));
            String parentTitle = parent == null ? null : stringValue(parent.get("title"));
            insights.add("la task con cua " + (parentKey == null ? "parent-unknown" : parentKey)
                    + (parentTitle == null || parentTitle.isBlank() ? "" : " (" + parentTitle + ")"));
        }

        if (issueId != null) {
            int childCount = childCountByParentId.getOrDefault(issueId, 0);
            if (childCount > 0) {
                insights.add("dang la parent cua " + childCount + " task con");
            }
        }

        if (insights.isEmpty()) {
            insights.add("khong co lien ket parent/child ro rang");
        }

        return String.join(", ", insights);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param typeById the type by id parameter
     * @param issueById the issue by id parameter
     * @param childCountByParentId the child count by parent id parameter
     * @param today the today parameter
     * @return the build task user action result
     */
    private String buildTaskUserAction(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            Map<String, String> typeById,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId,
            LocalDate today) {
        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String nStatus = normalize(statusName);
        String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
        String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "unknown");
        int pWeight = priorityWeight(priorityName);
        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        String description = normalize(stringValue(issue.get("description")));

        String parentId = stringValue(issue.get("parentId"));
        if (parentId != null && !parentId.isBlank()) {
            Map<String, Object> parent = issueById.get(parentId);
            String parentKey = parent == null ? "parent" : stringValue(parent.get("issueKey"));
            return "dong bo scope voi " + parentKey + ", cap nhat tien do task con va bao cao dependency som";
        }

        String issueId = stringValue(issue.get("id"));
        if (issueId != null && childCountByParentId.getOrDefault(issueId, 0) > 0) {
            return "chia ro owner/timebox cho task con, theo doi blocker cua tung subtask moi ngay";
        }

        String nType = normalize(typeName);
        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            return "xac dinh nguyen nhan blocker, dat nguoi go blocker va han giai quyet trong ngay";
        }
        if (due != null && due.isBefore(today)) {
            return "uu tien xu ly gap va cap nhat ETA moi cho stakeholder";
        }
        if ((nType.contains("bug") || nType.contains("loi")) && pWeight >= 3) {
            return "thuc hien fix + regression test ngay, sau do thong bao ket qua cho team";
        }
        if (description == null || description.isBlank() || description.length() < 40) {
            return "bo sung acceptance criteria, impact va test case truoc khi tiep tuc implement";
        }
        if (pWeight >= 3) {
            return "dua vao danh sach top priority va update tien do 2 lan/ngay";
        }
        return "tiep tuc theo sprint plan, dam bao commit scope va cap nhat progress deu";
    }

    /**
     * Parses project issue tool data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the parse int value result
     */
    private Integer parseIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param typeName the type name parameter
     * @param priorityName the priority name parameter
     * @param statusName the status name parameter
     * @param due the due parameter
     * @param storyPoints the story points parameter
     * @param today the today parameter
     * @param issueById the issue by id parameter
     * @param childCountByParentId the child count by parent id parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param typeById the type by id parameter
     * @return the build issue template result
     */
    private String buildIssueTemplate(Map<String, Object> issue,
            String typeName,
            String priorityName,
            String statusName,
            LocalDate due,
            Integer storyPoints,
            LocalDate today,
            Map<String, Map<String, Object>> issueById,
            Map<String, Integer> childCountByParentId,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            Map<String, String> typeById) {
        String title = stringValue(issue.get("title"));
        String descriptionRaw = stringValue(issue.get("description"));
        String description = normalize(descriptionRaw);
        String parentChild = buildParentChildInsight(issue, issueById, childCountByParentId);
        String riskInsight = buildTaskRiskInsight(title, description, due, statusName, priorityName, today);
        String userAction = buildTaskUserAction(issue, priorityById, statusById, typeById, issueById,
                childCountByParentId, today);
        String complexity = buildComplexityInsight(storyPoints, description, typeName);

        StringBuilder out = new StringBuilder();
        out.append("1. Tóm tắt:\n")
                .append("- Cần làm: ")
                .append(title == null ? "task nay" : title)
                .append(".\n")
                .append("- Mục tiêu: ")
                .append(summaryBusinessGoal(typeName, descriptionRaw))
                .append("\n\n");

        out.append("2. Phân tích kỹ thuật:\n")
                .append("- Loại bài toán: ")
                .append(classifyProblemType(typeName, description, title))
                .append("\n")
                .append("- Thành phần bị ảnh hưởng: ")
                .append(classifyImpactedComponents(typeName, description, title))
                .append("\n")
                .append("- Parent / Child: ")
                .append(parentChild)
                .append("\n")
                .append("- Độ phức tạp: ")
                .append(complexity)
                .append("\n\n");

        out.append("3. Hướng dẫn thực hiện:\n")
                .append(buildImplementationSteps(issue, typeName, priorityName, statusName, due, storyPoints,
                        descriptionRaw, parentChild))
                .append("\n\n");

        out.append("4. Breakdown:\n")
                .append(buildBreakdownItems(issue, storyPoints, parentChild, typeName))
                .append("\n\n");

        out.append("5. Rủi ro:\n")
                .append("- ").append(riskInsight).append("\n")
                .append("- Cần review kỹ xử lý lỗi và timeout.\n\n");

        out.append("6. Test cần làm:\n")
                .append(buildTestPlan(typeName, description, due))
                .append("\n\n");

        out.append("7. Gợi ý:\n")
                .append("- ").append(userAction).append("\n")
                .append("- Chốt Definition of Done rõ ràng trước khi merge.");

        return out.toString();
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param taskTargets the task targets parameter
     * @param typeById the type by id parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build concise priority analysis result
     */
    private String buildConcisePriorityAnalysis(List<Map<String, Object>> taskTargets,
            Map<String, String> typeById,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> sorted = taskTargets.stream()
                .sorted(Comparator.comparingInt(issue -> -compactPriorityScore(issue, priorityById, statusById, today)))
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder out = new StringBuilder();
        out.append("Ưu tiên công việc hiện tại (ngắn gọn):\n");

        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> issue = sorted.get(i);
            String key = stringValue(issue.get("issueKey"));
            String title = stringValue(issue.get("title"));
            String typeName = typeById.getOrDefault(stringValue(issue.get("issueTypeId")), "Task");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "Unknown");
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            Integer storyPoints = parseIntValue(issue.get("storyPoints"));

            String whatToDo = buildShortWhatToDo(typeName, title);
            String reason = buildShortPriorityReason(issue, priorityName, storyPoints, statusById, today);

            out.append(i + 1)
                    .append(". ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n")
                    .append("   - Làm gì: ").append(whatToDo).append("\n")
                    .append("   - Vì sao ưu tiên: ").append(reason).append("\n");
        }

        return out.toString().trim();
    }

    /**
     * Returns summary business goal for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param typeName the type name parameter
     * @param descriptionRaw the description raw parameter
     * @return the summary business goal result
     */
    private String summaryBusinessGoal(String typeName, String descriptionRaw) {
        String nType = normalize(typeName);
        String nDesc = normalize(descriptionRaw);
        if (nType.contains("bug") || nType.contains("loi")) {
            return "khac phuc loi, khoi phuc hanh vi dung va giam nguy co tai phat";
        }
        if (nDesc.contains("auth") || nDesc.contains("login") || nDesc.contains("token")) {
            return "bao dam xac thuc/phan quyen dung va an toan";
        }
        if (nDesc.contains("api") || nDesc.contains("integration") || nDesc.contains("sync")) {
            return "dam bao luong tich hop va dong bo du lieu on dinh";
        }
        return "hoan thanh tinh nang theo dung scope va chat luong";
    }

    /**
     * Returns compact priority score for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the compact priority score result
     */
    private int compactPriorityScore(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        int score = issueImportanceScore(issue, priorityById, statusById, today);
        Integer storyPoints = parseIntValue(issue.get("storyPoints"));
        if (storyPoints != null && storyPoints >= 8) {
            score += 10;
        } else if (storyPoints != null && storyPoints >= 5) {
            score += 5;
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            score += 10;
        } else if (age >= 7) {
            score += 5;
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            score += 5;
        }

        return score;
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param typeName the type name parameter
     * @param title the title parameter
     * @return the build short what to do result
     */
    private String buildShortWhatToDo(String typeName, String title) {
        String nType = normalize(typeName);
        if (nType.contains("bug") || nType.contains("loi")) {
            return "Fix lỗi chính và xác nhận không tái phát bằng regression test.";
        }
        if (nType.contains("story") || nType.contains("epic")) {
            return "Tách scope, chốt acceptance criteria, rồi triển khai luồng cốt lõi trước.";
        }
        String shortTitle = title == null ? "task hiện tại" : title;
        return "Hoàn thành luồng chính của \"" + shortTitle + "\" và cập nhật trạng thái trong ngày.";
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityName the priority name parameter
     * @param storyPoints the story points parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build short priority reason result
     */
    private String buildShortPriorityReason(Map<String, Object> issue,
            String priorityName,
            Integer storyPoints,
            Map<String, String> statusById,
            LocalDate today) {
        List<String> reasons = new ArrayList<>();
        int pWeight = priorityWeight(priorityName);
        int score = compactPriorityEvidenceScore(issue, priorityName, statusById, today);

        reasons.add("điểm ưu tiên=" + score);

        if (pWeight >= 4) {
            reasons.add("priority rất cao (" + priorityName + ")");
        } else if (pWeight == 3) {
            reasons.add("priority cao (" + priorityName + ")");
        }

        if (storyPoints != null && storyPoints >= 8) {
            reasons.add("SP=" + storyPoints + " (lớn)");
        } else if (storyPoints != null && storyPoints >= 5) {
            reasons.add("SP=" + storyPoints + " (tương đối cao)");
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            reasons.add("đã tồn tại lâu: " + age + " ngày");
        } else if (age >= 7) {
            reasons.add("đang tồn đọng: " + age + " ngày");
        }

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String nStatus = normalize(statusName);
        if (nStatus.contains("block") || nStatus.contains("stuck") || nStatus.contains("hold")) {
            reasons.add("status hiện tại=" + statusName + " (blocker/stuck)");
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            reasons.add("độ khó kỹ thuật cao (mô tả có integration/migration/auth/payment)");
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null && due.isBefore(today)) {
            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(due, today);
            reasons.add("đã quá hạn " + overdueDays + " ngày (due=" + due + ")");
        } else if (due != null && due.isEqual(today)) {
            reasons.add("đến hạn hôm nay (due=" + due + ")");
        }

        if (reasons.isEmpty()) {
            reasons.add("ảnh hưởng trực tiếp tiến độ sprint (chưa có chỉ báo rủi ro mạnh khác)");
        }
        return String.join(", ", reasons) + ".";
    }

    /**
     * Returns compact priority evidence score for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityName the priority name parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the compact priority evidence score result
     */
    private int compactPriorityEvidenceScore(Map<String, Object> issue,
            String priorityName,
            Map<String, String> statusById,
            LocalDate today) {
        int score = priorityWeight(priorityName) * 10;

        String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
        String status = normalize(statusName);
        if (status.contains("block") || status.contains("stuck") || status.contains("hold")) {
            score += 15;
        }

        Integer storyPoints = parseIntValue(issue.get("storyPoints"));
        if (storyPoints != null && storyPoints >= 8) {
            score += 10;
        } else if (storyPoints != null && storyPoints >= 5) {
            score += 5;
        }

        int age = issueAgeDays(issue);
        if (age >= 14) {
            score += 10;
        } else if (age >= 7) {
            score += 5;
        }

        LocalDate due = parseDate(stringValue(issue.get("dueDate")));
        if (due != null && due.isBefore(today)) {
            score += 20;
        } else if (due != null && due.isEqual(today)) {
            score += 10;
        }

        String desc = normalize(stringValue(issue.get("description")));
        if (desc.contains("integration") || desc.contains("migration") || desc.contains("auth")
                || desc.contains("payment")) {
            score += 5;
        }

        return score;
    }

    /**
     * Returns issue age days for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @return the issue age days result
     */
    private int issueAgeDays(Map<String, Object> issue) {
        String rawCreatedAt = stringValue(issue.get("createdAt"));
        if (rawCreatedAt == null || rawCreatedAt.isBlank()) {
            return 0;
        }

        LocalDate createdDate = parseFlexibleDate(rawCreatedAt);
        if (createdDate == null) {
            return 0;
        }
        return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(createdDate, LocalDate.now()));
    }

    /**
     * Parses project issue tool data.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @return the parse flexible date result
     */
    private LocalDate parseFlexibleDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10));
            }
            return LocalDate.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns classify problem type for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param typeName the type name parameter
     * @param description the description parameter
     * @param title the title parameter
     * @return the classify problem type result
     */
    private String classifyProblemType(String typeName, String description, String title) {
        String nType = normalize(typeName);
        String nDesc = description == null ? "" : description;
        String nTitle = normalize(title);
        List<String> tags = new ArrayList<>();

        if (nType.contains("bug") || nType.contains("loi") || nTitle.contains("fix") || nTitle.contains("error")) {
            tags.add("Bug fix");
        } else {
            tags.add("Feature/Task");
        }
        if (nDesc.contains("auth") || nDesc.contains("token") || nDesc.contains("permission")) {
            tags.add("Auth/Security");
        }
        if (nDesc.contains("api") || nDesc.contains("integration") || nDesc.contains("webhook")) {
            tags.add("Integration/API");
        }
        if (nDesc.contains("ui") || nDesc.contains("frontend") || nDesc.contains("screen")) {
            tags.add("UI/UX");
        }
        if (nDesc.contains("db") || nDesc.contains("migration") || nDesc.contains("query")) {
            tags.add("Data/DB");
        }

        return String.join(" / ", tags);
    }

    /**
     * Returns classify impacted components for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param typeName the type name parameter
     * @param description the description parameter
     * @param title the title parameter
     * @return the classify impacted components result
     */
    private String classifyImpactedComponents(String typeName, String description, String title) {
        String n = (description == null ? "" : description) + " " + normalize(typeName) + " " + normalize(title);
        List<String> components = new ArrayList<>();

        components.add("BE logic");
        if (n.contains("api") || n.contains("integration") || n.contains("endpoint")) {
            components.add("API contract");
        }
        if (n.contains("db") || n.contains("migration") || n.contains("query")) {
            components.add("DB schema/query");
        }
        if (n.contains("ui") || n.contains("frontend") || n.contains("screen")) {
            components.add("FE flow");
        }
        if (n.contains("auth") || n.contains("token") || n.contains("permission")) {
            components.add("Security layer");
        }

        return String.join(", ", components);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param typeName the type name parameter
     * @param priorityName the priority name parameter
     * @param statusName the status name parameter
     * @param due the due parameter
     * @param storyPoints the story points parameter
     * @param descriptionRaw the description raw parameter
     * @param parentChild the parent child parameter
     * @return the build implementation steps result
     */
    private String buildImplementationSteps(Map<String, Object> issue,
            String typeName,
            String priorityName,
            String statusName,
            LocalDate due,
            Integer storyPoints,
            String descriptionRaw,
            String parentChild) {
        StringBuilder sb = new StringBuilder();
        String nType = normalize(typeName);

        sb.append("- Bước 1:\n")
                .append("  + Làm gì: Chốt scope và tiêu chí hoàn thành từ title/description, xác nhận phụ thuộc parent-child.\n")
                .append("  + Output mong đợi: Checklist việc phải làm + thứ tự ưu tiên.\n")
                .append("  + API/logic/DB: Chốt contract và rule trước khi code.\n");

        sb.append("- Bước 2:\n")
                .append("  + Làm gì: Thiết kế thay đổi chính ở API/service/repository (validation + error code).\n")
                .append("  + Output mong đợi: Thiết kế ngắn gọn, dev khác đọc là làm được ngay.\n")
                .append("  + API/logic/DB: Xác định endpoint/field/query bị ảnh hưởng.\n");

        sb.append("- Bước 3:\n")
                .append("  + Làm gì: Implement luồng chính end-to-end, sau đó cập nhật FE nếu đổi contract.\n")
                .append("  + Output mong đợi: Chạy được happy path và xử lý lỗi cơ bản.\n")
                .append("  + API/logic/DB: Mapping DTO/entity đúng, migration/index nếu cần.\n");

        sb.append("- Bước 4:\n")
                .append("  + Làm gì: Viết test trọng yếu và chạy regression ở các luồng liên quan.\n")
                .append("  + Output mong đợi: Test pass, không vỡ tính năng cũ.\n")
                .append("  + API/logic/DB: Cover validation, permission, consistency.\n");

        if ((storyPoints != null && storyPoints >= 8) || nType.contains("epic") || nType.contains("story")) {
            sb.append("- Bước bổ sung (task lớn):\n")
                    .append("  + Làm gì: Chia milestone nhỏ để giao từng phần.\n")
                    .append("  + Output mong đợi: Có plan theo phase, kiểm soát rủi ro tốt hơn.\n")
                    .append("  + API/logic/DB: Ưu tiên phần tạo contract ổn định trước.\n");
        }

        if (due != null) {
            sb.append("- Ghi chú deadline: due=").append(due).append(", priority=").append(priorityName)
                    .append(", status hiện tại=").append(statusName).append(".\n");
        }
        sb.append("- Liên hệ parent/child: ").append(parentChild).append(".");

        return sb.toString();
    }

    /**
     * Returns has any phrase for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param text the text parameter
     * @param phrases the phrases parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean hasAnyPhrase(String text, String... phrases) {
        for (String phrase : phrases) {
            if (matchesPhraseFuzzy(text, normalize(phrase))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns matches phrase fuzzy for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param text the text parameter
     * @param phrase the phrase parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean matchesPhraseFuzzy(String text, String phrase) {
        if (text.contains(phrase)) {
            return true;
        }

        String compactText = text.replace(" ", "");
        String compactPhrase = phrase.replace(" ", "");
        if (compactText.contains(compactPhrase)) {
            return true;
        }

        String[] textWords = text.split("\\s+");
        String[] phraseWords = phrase.split("\\s+");

        for (String phraseWord : phraseWords) {
            boolean found = false;
            for (String textWord : textWords) {
                if (isSimilarToken(textWord, phraseWord)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns is similar token for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param a the a parameter
     * @param b the b parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isSimilarToken(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int maxLen = Math.max(a.length(), b.length());
        int threshold = maxLen <= 5 ? 1 : 2;
        return levenshteinDistance(a, b) <= threshold;
    }

    /**
     * Returns levenshtein distance for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param a the a parameter
     * @param b the b parameter
     * @return the levenshtein distance result
     */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Create or prepare the requested domain result.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param storyPoints the story points parameter
     * @param parentChild the parent child parameter
     * @param typeName the type name parameter
     * @return the build breakdown items result
     */
    private String buildBreakdownItems(Map<String, Object> issue,
            Integer storyPoints,
            String parentChild,
            String typeName) {
        List<String> items = new ArrayList<>();
        String nType = normalize(typeName);

        items.add("- Task 1: Clarify requirement + cập nhật acceptance criteria.");
        items.add("- Task 2: Implement logic chính + validation.");
        items.add("- Task 3: Cập nhật API contract/DTO + error code.");
        items.add("- Task 4: Viết test + regression.");

        if (storyPoints != null && storyPoints >= 8) {
            items.add("- Task 5: Tách thành subtask theo module để review độc lập.");
        }
        if (parentChild.contains("parent") || parentChild.contains("task con")) {
            items.add("- Task phụ: Đồng bộ dependency với parent/child trước khi close.");
        }
        if (nType.contains("bug") || nType.contains("loi")) {
            items.add("- Task phụ: Viết root cause note + prevention checklist.");
        }

        return String.join("\n", items);
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Apply the requested state changes according to the domain rules.</li>
     * </ul>
     *
     * @param typeName the type name parameter
     * @param description the description parameter
     * @param due the due parameter
     * @return the build test plan result
     */
    private String buildTestPlan(String typeName, String description, LocalDate due) {
        String nType = normalize(typeName);
        String nDesc = description == null ? "" : description;
        StringBuilder sb = new StringBuilder();

        sb.append("- Unit test: cover business rule chính, validation, error handling.\n")
                .append("- Integration test: cover endpoint-service-repository và mapping dữ liệu.\n")
                .append("- Case quan trọng cần cover:\n")
                .append("  + Happy path (dữ liệu hợp lệ).\n")
                .append("  + Invalid input/permission denied/not found.\n")
                .append("  + Concurrent update hoặc duplicate request.\n");

        if (nType.contains("bug") || nType.contains("loi")) {
            sb.append("  + Regression case cho bug gốc để tránh tái phát.\n");
        }
        if (nDesc.contains("auth") || nDesc.contains("token") || nDesc.contains("permission")) {
            sb.append("  + Security case: token invalid/expired, role không đủ quyền.\n");
        }
        if (due != null) {
            sb.append("  + Smoke test nhanh theo deadline due=").append(due).append(" trước khi merge.\n");
        }

        return sb.toString().trim();
    }

    /**
     * Returns string value for project issue tool processing.
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
     * Returns structured for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param payload the payload parameter
     * @return the structured result
     */
    private String structured(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"type\":\"error\",\"title\":\"Không thể tạo phản hồi\",\"summary\":\"Mình chưa thể tạo phản hồi lúc này.\"}";
        }
    }

    /**
     * Returns structured error for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param message the message parameter
     * @return the structured error result
     */
    private String structuredError(String message) {
        return structured(Map.of(
                "type", "error",
                "title", "Mình cần thêm thông tin",
                "summary", message));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param actionType the action type parameter
     * @param title the title parameter
     * @param summary the summary parameter
     * @param payload the payload parameter
     * @return the build confirmation result
     */
    private String buildConfirmation(String actionType, String title, String summary, Map<String, Object> payload) {
        return structured(Map.of(
                "type", "confirmation",
                "title", title,
                "summary", summary,
                "actions", List.of(Map.of(
                        "type", actionType,
                        "label", "Xác nhận",
                        "payload", payload))));
    }

    /**
     * Returns metric for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param label the label parameter
     * @param value the value parameter
     * @return the metric result
     */
    private Map<String, Object> metric(String label, Object value) {
        return Map.of("label", label, "value", value == null ? "" : value);
    }

    /**
     * Returns section for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param title the title parameter
     * @param items the items parameter
     * @return the section result
     */
    private Map<String, Object> section(String title, List<String> items) {
        return Map.of("title", title, "items", items == null ? List.of() : items);
    }

    /**
     * Returns issue summaries for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the matching result collection
     */
    private List<String> issueSummaries(List<Map<String, Object>> issues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        if (issues == null || issues.isEmpty()) {
            return List.of("Chưa có issue nổi bật.");
        }
        return issues.stream()
                .limit(7)
                .map(issue -> {
                    String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
                    String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
                    String dueDate = stringValue(issue.get("dueDate"));
                    return formatIssueCardLine(issue, statusName, priorityName, dueDate,
                            buildStandupReason(issue, priorityById, statusById, today));
                })
                .toList();
    }

    /**
     * Normalizes project issue tool content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the normalize issues for prompt result
     */
    private List<Map<String, Object>> normalizeIssuesForPrompt(List<Map<String, Object>> issues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        if (issues == null) {
            return List.of();
        }
        return issues.stream()
                .limit(7)
                .map(issue -> normalizeIssueForPrompt(issue, priorityById, statusById, Map.of(
                        "reason", buildStandupReason(issue, priorityById, statusById, today))))
                .toList();
    }

    /**
     * Normalizes project issue tool content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param extra the extra parameter
     * @return the normalize issue for prompt result
     */
    private Map<String, Object> normalizeIssueForPrompt(Map<String, Object> issue,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            Map<String, Object> extra) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("issueKey", cardValue(stringValue(issue.get("issueKey")), "Issue chưa có key"));
        normalized.put("title", cardValue(stringValue(issue.get("title")), "Chưa có tiêu đề"));
        normalized.put("statusName", cleanDisplay(statusById.get(stringValue(issue.get("statusId"))), "Chưa phân loại"));
        normalized.put("priorityName", cleanDisplay(priorityById.get(stringValue(issue.get("priorityId"))), "Chưa phân loại"));
        normalized.put("assigneeName", cleanDisplay(firstNonBlank(
                stringValue(issue.get("assigneeName")),
                stringValue(issue.get("assignee")),
                stringValue(issue.get("assigneeFullName"))), "Chưa có người phụ trách"));
        normalized.put("dueDate", cleanDisplay(stringValue(issue.get("dueDate")), "Chưa có hạn chót"));
        normalized.put("sprintName", cleanDisplay(stringValue(issue.get("sprintName")), ""));
        normalized.put("updatedAt", cleanDisplay(stringValue(issue.get("updatedAt")), ""));
        if (extra != null) {
            normalized.putAll(extra);
        }
        return normalized;
    }

    /**
     * Returns detect report type for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param normalized the normalized parameter
     * @return the detect report type result
     */
    private ReportType detectReportType(String normalized) {
        if (hasAnyPhrase(normalized, "tom tat tien do", "tom tat tinh trang", "tinh trang du an",
                "tinh hinh du an", "bao cao tien do", "progress summary", "standup", "bao cao standup")) {
            return ReportType.SUMMARY_PROGRESS;
        }
        if (hasAnyPhrase(normalized, "lap ke hoach hom nay", "ke hoach hom nay", "5 viec uu tien",
                "nam viec uu tien", "top 5", "de xuat viec can lam", "de xuat buoc tiep theo",
                "buoc tiep theo", "viec can lam", "uu tien hom nay")) {
            return ReportType.DAILY_PLAN;
        }
        if (hasAnyPhrase(normalized, "phan tich rui ro", "rui ro", "risk", "blocker", "stuck", "ket",
                "tre deadline", "qua han")) {
            return ReportType.RISK_ANALYSIS;
        }
        if (hasAnyPhrase(normalized, "ai dang qua tai", "qua tai", "workload", "tai cong viec")) {
            return ReportType.MEMBER_WORKLOAD;
        }
        if (hasAnyPhrase(normalized, "deadline", "han chot", "qua han", "tre deadline")) {
            return ReportType.DEADLINE_CHECK;
        }
        if (hasAnyPhrase(normalized,
                "dem so issue", "bao nhieu issue", "thong ke", "thong ke theo status", "thong ke theo priority",
                "status va priority", "count issue", "report")) {
            return ReportType.REPORT_STATS;
        }
        return ReportType.GENERAL_ANALYSIS;
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @return the build issue statistics report result
     */
    private String buildIssueStatisticsReport(List<Map<String, Object>> issues,
            Map<String, String> priorityById,
            Map<String, String> statusById) {
        Map<String, Long> byStatus = countByDisplayName(issues, statusById, "statusId", "Chưa phân loại");
        Map<String, Long> byPriority = countByDisplayName(issues, priorityById, "priorityId", "Chưa phân loại");

        StringBuilder out = new StringBuilder();
        out.append("## Thống kê issue\n\n")
                .append("Tổng số issue: ").append(issues.size()).append("\n\n")
                .append("### Theo trạng thái\n\n")
                .append("| Trạng thái | Số issue |\n")
                .append("|---|---:|\n");
        byStatus.forEach((status, count) -> out.append("| ").append(status).append(" | ").append(count).append(" |\n"));

        out.append("\n### Theo priority\n\n")
                .append("| Priority | Số issue |\n")
                .append("|---|---:|\n");
        byPriority.forEach((priority, count) -> out.append("| ").append(priority).append(" | ").append(count).append(" |\n"));

        out.append("\nMình chỉ thống kê theo nhóm để dễ đọc, không liệt kê toàn bộ issue ở đây.");
        return structured(Map.of(
                "type", "summary",
                "title", "Thống kê issue",
                "summary", "Tổng số issue: " + issues.size(),
                "metrics", List.of(metric("Tổng issue", issues.size())),
                "sections", List.of(
                        section("Theo trạng thái", byStatus.entrySet().stream()
                                .map(e -> e.getKey() + ": " + e.getValue()).toList()),
                        section("Theo priority", byPriority.entrySet().stream()
                                .map(e -> e.getKey() + ": " + e.getValue()).toList()))));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param taskTargets the task targets parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build daily plan result
     */
    private String buildDailyPlan(List<Map<String, Object>> taskTargets,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> top = taskTargets.stream()
                .filter(issue -> !isDoneStatus(normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""))))
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(5)
                .collect(Collectors.toList());
        if (top.isEmpty()) {
            top = taskTargets.stream()
                    .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                    .limit(5)
                    .collect(Collectors.toList());
        }

        StringBuilder out = new StringBuilder();
        out.append("## Top 5 việc nên ưu tiên hôm nay\n");
        for (int i = 0; i < top.size(); i++) {
            Map<String, Object> issue = top.get(i);
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            String reason = buildStandupReason(issue, priorityById, statusById, today);
            out.append(i + 1).append(". ")
                    .append(formatIssueCardLine(issue, statusName, priorityName, dueDate, reason))
                    .append("\n");
        }

        out.append("\n## Lý do ưu tiên\n")
                .append("- Ưu tiên issue quá hạn, gần hạn, priority cao hoặc đang bị kẹt.\n")
                .append("- Giữ số lượng việc trong ngày ở mức có thể hoàn thành để demo tiến độ rõ ràng.\n\n")
                .append("## Người/nhóm cần phối hợp\n")
                .append("- Trao đổi với assignee của các issue priority cao hoặc đang bị kẹt.\n")
                .append("- Nếu issue chưa có người phụ trách, cần chốt owner trước khi bắt đầu.\n\n")
                .append("## Kết quả kỳ vọng cuối ngày\n")
                .append("- Cập nhật trạng thái cho các issue đã xử lý.\n")
                .append("- Gỡ được blocker chính hoặc có ETA rõ ràng cho các việc còn mở.");
        appendRemainingIssueNote(out, taskTargets.size(), top.size());
        return structured(Map.of(
                "type", "daily_plan",
                "title", "Lập kế hoạch hôm nay",
                "summary", "Top " + top.size() + " việc nên ưu tiên hôm nay.",
                "metrics", List.of(metric("Việc ưu tiên", top.size())),
                "sections", List.of(
                        section("Lý do ưu tiên", List.of(
                                "Ưu tiên issue quá hạn, gần hạn, priority cao hoặc đang bị kẹt.",
                                "Giữ số lượng việc trong ngày ở mức có thể hoàn thành để demo tiến độ rõ ràng.")),
                        section("Người/nhóm cần phối hợp", List.of(
                                "Trao đổi với assignee của các issue priority cao hoặc đang bị kẹt.",
                                "Nếu issue chưa có người phụ trách, cần chốt owner trước khi bắt đầu.")),
                        section("Kết quả kỳ vọng cuối ngày", List.of(
                                "Cập nhật trạng thái cho các issue đã xử lý.",
                                "Gỡ được blocker chính hoặc có ETA rõ ràng cho các việc còn mở."))),
                "issues", normalizeIssuesForPrompt(top, priorityById, statusById, today),
                "actions", List.of("Chốt owner", "Xử lý issue quá hạn", "Cập nhật tiến độ cuối ngày")));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build member workload report result
     */
    private String buildMemberWorkloadReport(List<Map<String, Object>> issues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        Map<String, Long> workload = issues.stream()
                .filter(issue -> !isDoneStatus(normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""))))
                .collect(Collectors.groupingBy(issue -> cleanDisplay(firstNonBlank(
                        stringValue(issue.get("assigneeName")),
                        stringValue(issue.get("assignee")),
                        stringValue(issue.get("assigneeFullName"))), "Chưa có người phụ trách"),
                        LinkedHashMap::new, Collectors.counting()));
        List<String> rows = workload.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + ": " + e.getValue() + " issue đang mở")
                .toList();
        return structured(Map.of(
                "type", "summary",
                "title", "Phân tích workload thành viên",
                "summary", rows.isEmpty() ? "Chưa có issue mở để đánh giá workload." : "Các thành viên có nhiều issue mở nhất đang cần được theo dõi.",
                "sections", List.of(section("Ai đang quá tải?", rows),
                        section("Hành động đề xuất", List.of(
                                "Giảm tải cho người có nhiều issue mở hoặc issue priority cao.",
                                "Chốt owner cho các issue chưa có người phụ trách.",
                                "Ưu tiên xử lý blocker/quá hạn trước khi nhận thêm việc."))),
                "issues", normalizeIssuesForPrompt(issues.stream()
                        .filter(issue -> !isDoneStatus(normalize(statusById.getOrDefault(stringValue(issue.get("statusId")), ""))))
                        .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                        .limit(7).toList(), priorityById, statusById, today)));
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param openIssues the open issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     * @return the build deadline check result
     */
    private String buildDeadlineCheck(List<Map<String, Object>> openIssues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        List<Map<String, Object>> deadlineIssues = openIssues.stream()
                .filter(issue -> isOverdue(issue, today) || isDueSoon(issue, today))
                .sorted(Comparator.comparingInt(issue -> -issueImportanceScore(issue, priorityById, statusById, today)))
                .limit(7)
                .toList();
        return structured(Map.of(
                "type", "risk_analysis",
                "title", "Kiểm tra deadline",
                "summary", deadlineIssues.isEmpty()
                        ? "Chưa thấy issue mở nào quá hạn hoặc sát hạn."
                        : "Có " + deadlineIssues.size() + " issue cần chú ý về deadline.",
                "sections", List.of(section("Hành động đề xuất", List.of(
                        "Cập nhật ETA cho issue quá hạn.",
                        "Đẩy issue sát hạn vào kế hoạch hôm nay.",
                        "Thông báo sớm cho stakeholder nếu cần đổi scope."))),
                "issues", normalizeIssuesForPrompt(deadlineIssues, priorityById, statusById, today)));
    }

    /**
     * Counts project issue tool records.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issues the issues parameter
     * @param displayById the display by id parameter
     * @param field the field parameter
     * @param fallback the fallback parameter
     * @return the count by display name result
     */
    private Map<String, Long> countByDisplayName(List<Map<String, Object>> issues,
            Map<String, String> displayById,
            String field,
            String fallback) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> issue : issues) {
            String name = cleanDisplay(displayById.get(stringValue(issue.get(field))), fallback);
            counts.put(name, counts.getOrDefault(name, 0L) + 1L);
        }
        return counts;
    }

    /**
     * Performs append issue lines for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @param out the out parameter
     * @param issues the issues parameter
     * @param priorityById the priority by id parameter
     * @param statusById the status by id parameter
     * @param today the today parameter
     */
    private void appendIssueLines(StringBuilder out,
            List<Map<String, Object>> issues,
            Map<String, String> priorityById,
            Map<String, String> statusById,
            LocalDate today) {
        if (issues.isEmpty()) {
            out.append("- Chưa có issue nổi bật.\n");
            return;
        }
        int limit = Math.min(issues.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = issues.get(i);
            String statusName = statusById.getOrDefault(stringValue(issue.get("statusId")), "unknown");
            String priorityName = priorityById.getOrDefault(stringValue(issue.get("priorityId")), "unknown");
            String dueDate = stringValue(issue.get("dueDate"));
            out.append("- ").append(formatIssueCardLine(issue, statusName, priorityName, dueDate,
                    buildStandupReason(issue, priorityById, statusById, today))).append("\n");
        }
    }

    /**
     * Performs append remaining issue note for project issue tool processing.
     *
     * @param out the out parameter
     * @param total the total parameter
     * @param shown the shown parameter
     */
    private void appendRemainingIssueNote(StringBuilder out, int total, int shown) {
        int remaining = total - shown;
        if (remaining > 0) {
            out.append("\nCòn ").append(remaining).append(" issue khác, bạn có thể xem chi tiết ở tab Issues.");
        }
    }

    /**
     * Builds project issue tool data for downstream processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param openCount the open count parameter
     * @param overdueCount the overdue count parameter
     * @param blockedCount the blocked count parameter
     * @param totalCount the total count parameter
     * @return the build project health comment result
     */
    private String buildProjectHealthComment(long openCount, long overdueCount, long blockedCount, long totalCount) {
        if (totalCount == 0) {
            return "Dự án chưa có issue để đánh giá.";
        }
        if (blockedCount > 0 || overdueCount >= 3) {
            return "Sức khỏe dự án đang ở mức cần chú ý. Nên xử lý blocker và issue quá hạn trước khi nhận thêm việc mới.";
        }
        if (openCount > totalCount / 2) {
            return "Dự án đang ở mức trung bình. Khối lượng việc mở còn nhiều, nên tập trung đóng các issue gần hoàn thành.";
        }
        return "Dự án đang khá ổn. Tiếp tục giữ nhịp cập nhật trạng thái và xử lý các issue ưu tiên cao.";
    }

    /**
     * Returns clean display for project issue tool processing.
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
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        String normalized = normalize(trimmed);
        if (normalized.equals("unknown") || normalized.equals("null") || normalized.equals("undefined")) {
            return fallback;
        }
        return trimmed.replace('|', '/').replaceAll("\\s+", " ");
    }

    /**
     * Returns format issue card line for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param issue the issue parameter
     * @param statusName the status name parameter
     * @param priorityName the priority name parameter
     * @param dueDate the due date parameter
     * @param reason the reason parameter
     * @return the format issue card line result
     */
    private String formatIssueCardLine(Map<String, Object> issue,
            String statusName,
            String priorityName,
            String dueDate,
            String reason) {
        StringBuilder out = new StringBuilder();
        out.append(cardValue(stringValue(issue.get("issueKey")), "Issue chưa có key"))
                .append(" | ")
                .append(cardValue(stringValue(issue.get("title")), "Chưa có tiêu đề"))
                .append(" | status=")
                .append(cleanDisplay(statusName, "Chưa phân loại"))
                .append(" | priority=")
                .append(cleanDisplay(priorityName, "Chưa phân loại"));

        if (dueDate != null && !dueDate.isBlank()) {
            out.append(" | due=").append(cleanDisplay(dueDate, "Chưa có hạn chót"));
        } else {
            out.append(" | due=Chưa có hạn chót");
        }
        if (reason != null && !reason.isBlank()) {
            out.append(" | reason=").append(cardValue(reason, ""));
        }
        return out.toString();
    }

    /**
     * Returns card value for project issue tool processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param value the value parameter
     * @param fallback the fallback parameter
     * @return the card value result
     */
    private String cardValue(String value, String fallback) {
        String text = value == null || value.isBlank() ? fallback : value;
        return text.replace('|', '/').replaceAll("\\s+", " ").trim();
    }

    /**
     * Normalizes project issue tool content.
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
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
    }
}
