package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.model.agent.AgentAction;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.model.agent.AgentPlan;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentPlannerService {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("\\b([A-Z][A-Z0-9]*-\\d+)\\b");
    private static final Pattern STATUS_AFTER_PATTERN = Pattern.compile(
            "(?:sang|to|thanh|status|trang thai)\\s+(?:trang thai\\s+)?([\\p{L}\\p{N}_-]+(?:\\s+[\\p{L}\\p{N}_-]+){0,4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final AgentIntentRouterService intentRouterService;
    private final PendingActionStore pendingActionStore;

    public AgentPlannerService(AgentIntentRouterService intentRouterService,
            PendingActionStore pendingActionStore) {
        this.intentRouterService = intentRouterService;
        this.pendingActionStore = pendingActionStore;
    }

    public AgentPlan plan(String userId,
            String conversationId,
            AgentChatRequest request,
            String conversationContext) {
        String question = request.question() == null ? "" : request.question();
        String normalized = normalize(question);

        if (isConfirmation(normalized)) {
            Optional<?> pending = pendingActionStore.find(conversationId, userId);
            if (pending.isPresent()) {
                return AgentPlan.clarify(
                        AgentIntent.ISSUE_UPDATE,
                        "Minh da chuan bi thao tac nay. Ban bam nut Allow ben duoi tin nhan xac nhan de thuc hien cap nhat nhe.",
                        List.of("allowAction"));
            }
            return AgentPlan.clarify(
                    AgentIntent.ISSUE_UPDATE,
                    "Mình chưa thấy thao tác nào đang chờ xác nhận. Bạn nói rõ issue và trạng thái cần cập nhật nhé.",
                    List.of("pendingAction"));
        }

        if (looksLikeCreateIssue(normalized)) {
            return planIssueWrite(AgentIntent.ISSUE_UPDATE, question, normalized, request.projectId());
        }
        if (looksLikeExplicitIssueWrite(question, normalized)) {
            return planIssueWrite(AgentIntent.ISSUE_UPDATE, question, normalized, request.projectId());
        }

        AgentIntent intent = intentRouterService.route(
                question,
                request.projectId(),
                request.selectedDocumentIds(),
                conversationContext).intent();
        intent = refineProjectIntent(intent, normalized);

        if (intent == AgentIntent.ISSUE_ACTION || intent == AgentIntent.ISSUE_UPDATE) {
            return planIssueWrite(intent, question, normalized, request.projectId());
        }

        if (isReadProjectIntent(intent)) {
            if (request.projectId() == null || request.projectId().isBlank()) {
                return AgentPlan.clarify(
                        intent,
                        "Mình cần biết dự án hiện tại trước khi lấy dữ liệu project.",
                        List.of("projectId"));
            }
            return new AgentPlan(
                    AgentAction.READ_PROJECT,
                    intent,
                    0.88,
                    readToolFor(intent),
                    List.of("projectId"),
                    Map.of("projectId", request.projectId(), "question", question),
                    List.of(),
                    "Read project data and answer with grounded facts.",
                    false,
                    "");
        }

        return new AgentPlan(
                AgentAction.ANSWER,
                intent,
                intent == AgentIntent.GENERAL_CHAT ? 0.7 : 0.82,
                "",
                List.of(),
                Map.of("question", question),
                List.of(),
                "Answer using OpenRouter and available context.",
                false,
                "");
    }

    private AgentPlan planIssueWrite(AgentIntent intent, String question, String normalized, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return AgentPlan.clarify(
                    intent,
                    "Mình cần biết dự án hiện tại trước khi cập nhật issue.",
                    List.of("projectId"));
        }

        if (looksLikeCreateIssue(normalized)) {
            return new AgentPlan(
                    AgentAction.PROPOSE_WRITE,
                    intent,
                    0.82,
                    "create_issue",
                    List.of("projectId", "issueTypeId", "title"),
                    Map.of("projectId", projectId, "instruction", question),
                    List.of("issueTypeId", "title"),
                    "Create an issue after required fields are available and confirmed.",
                    true,
                    "");
        }

        String issueKey = extractIssueKey(question);
        if (issueKey == null) {
            return AgentPlan.clarify(
                    intent,
                    "Bạn muốn cập nhật issue nào? Hãy gửi issue key, ví dụ IEMS2-8.",
                    List.of("issueKey"));
        }

        if (containsAny(normalized, "gan", "assign", "assignee", "nguoi phu trach")) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            resolved.put("projectId", projectId);
            resolved.put("issueKey", issueKey);
            resolved.put("assigneeText", extractAssigneeText(question));
            return new AgentPlan(
                    AgentAction.PROPOSE_WRITE,
                    intent,
                    0.86,
                    "assign_issue",
                    List.of("projectId", "issueKey", "assigneeId"),
                    resolved,
                    List.of("assigneeId"),
                    "Assign an issue after confirmation.",
                    true,
                    "");
        }

        String status = extractTargetStatus(question, normalized);
        if (status == null || status.isBlank()) {
            return AgentPlan.clarify(
                    intent,
                    "Bạn muốn chuyển issue sang trạng thái nào? Ví dụ: sang Done.",
                    List.of("targetStatus"));
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("projectId", projectId);
        resolved.put("issueKey", issueKey);
        resolved.put("targetStatus", status);
        return new AgentPlan(
                AgentAction.PROPOSE_WRITE,
                intent,
                0.9,
                "update_issue_status",
                List.of("projectId", "issueKey", "targetStatus"),
                resolved,
                List.of(),
                "Prepare issue status update and ask for confirmation.",
                true,
                "");
    }

    private static boolean isReadProjectIntent(AgentIntent intent) {
        return intent == AgentIntent.ISSUE_QUERY
                || intent == AgentIntent.ISSUE_ANALYSIS
                || intent == AgentIntent.PROJECT_SUMMARY
                || intent == AgentIntent.DAILY_PLAN
                || intent == AgentIntent.RISK_ANALYSIS
                || intent == AgentIntent.SPRINT_REPORT
                || intent == AgentIntent.SPRINT_SUMMARY
                || intent == AgentIntent.ISSUE_SEARCH
                || intent == AgentIntent.MEMBER_WORKLOAD
                || intent == AgentIntent.DEADLINE_CHECK;
    }

    private static String readToolFor(AgentIntent intent) {
        return switch (intent) {
            case DAILY_PLAN -> "daily_plan";
            case ISSUE_QUERY, ISSUE_SEARCH -> "list_project_issues";
            case MEMBER_WORKLOAD -> "member_workload";
            case RISK_ANALYSIS, DEADLINE_CHECK -> "risk_signals";
            case SPRINT_REPORT, SPRINT_SUMMARY -> "sprint_report";
            default -> "project_overview";
        };
    }

    private static AgentIntent refineProjectIntent(AgentIntent intent, String normalized) {
        if (intent == AgentIntent.DAILY_PLAN) {
            return intent;
        }
        if (containsAny(normalized, "workload", "qua tai", "phan bo lai", "can ho tro")) {
            return AgentIntent.MEMBER_WORKLOAD;
        }
        if (containsAny(normalized, "standup", "blocker", "da xong", "dang lam")) {
            return AgentIntent.SPRINT_REPORT;
        }
        if (containsAny(normalized, "suc khoe", "thong ke theo trang thai", "tong quan")) {
            return AgentIntent.PROJECT_SUMMARY;
        }
        if (containsAny(normalized, "rui ro", "qua han", "tac dong", "hanh dong de xuat")) {
            return AgentIntent.RISK_ANALYSIS;
        }
        return intent;
    }

    private static String extractIssueKey(String question) {
        Matcher matcher = ISSUE_KEY_PATTERN.matcher(question == null ? "" : question.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractTargetStatus(String question, String normalized) {
        if (containsAny(normalized, "done", "hoan thanh", "closed")) {
            return "Done";
        }
        if (containsAny(normalized, "in progress", "dang lam", "doing", "review")) {
            return normalized.contains("review") ? "Review" : "In Progress";
        }
        if (containsAny(normalized, "todo", "to do", "chua lam", "open")) {
            return "To Do";
        }

        Matcher matcher = STATUS_AFTER_PATTERN.matcher(question == null ? "" : question);
        if (matcher.find()) {
            return cleanTargetStatus(matcher.group(1));
        }
        return "";
    }

    private static String cleanTargetStatus(String rawStatus) {
        if (rawStatus == null) {
            return "";
        }
        String cleaned = rawStatus
                .replaceAll("(?iu)\\b(giup|giúp|toi|tôi|minh|mình|nhe|nhé|di|đi|lai|lại)\\b.*$", "")
                .replaceAll("(?iu)^trang\\s+thai\\s+", "")
                .trim();
        return cleaned.replaceAll("\\s+", " ");
    }

    private static String extractAssigneeText(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String[] markers = {" cho ", " to ", " cho nguoi ", " cho ban "};
        String lower = " " + question.toLowerCase(Locale.ROOT) + " ";
        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                return question.substring(Math.min(question.length(), index + marker.length() - 1)).trim();
            }
        }
        return "";
    }

    private static boolean isConfirmation(String normalized) {
        return normalized.matches("^(dung|dung roi|ok|okay|oke|yes|xac nhan|confirm|cap nhat di|lam di|chay di).*$")
                || normalized.contains("ok cap nhat")
                || normalized.contains("dong y")
                || normalized.contains("cu lam");
    }

    private static boolean looksLikeIssueWrite(String normalized) {
        return containsAny(normalized,
                "cap nhat", "update", "chuyen", "doi", "assign", "gan", "assignee",
                "done", "hoan thanh", "in progress", "dang lam", "todo", "to do");
    }

    private static boolean looksLikeExplicitIssueWrite(String question, String normalized) {
        if (!looksLikeIssueWrite(normalized)) {
            return false;
        }
        if (extractIssueKey(question) != null) {
            return true;
        }
        if (containsAny(normalized, "task nay", "issue nay", "cong viec nay")) {
            return true;
        }
        return STATUS_AFTER_PATTERN.matcher(question == null ? "" : question).find()
                && containsAny(normalized, "sang", "to", "thanh", "status", "trang thai");
    }

    private static boolean looksLikeCreateIssue(String normalized) {
        return containsAny(normalized, "tao issue", "create issue", "tao task", "them issue");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
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
