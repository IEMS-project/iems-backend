package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentDecision;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.service.OpenRouterChatService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AgentOrchestratorService {

    private final AgentIntentRouterService intentRouterService;
    private final OpenRouterChatService openRouterChatService;
    private final ProjectIssueToolService projectIssueToolService;
    private final AgentResponseSanitizer responseSanitizer;

    public AgentOrchestratorService(AgentIntentRouterService intentRouterService,
            OpenRouterChatService openRouterChatService,
            ProjectIssueToolService projectIssueToolService,
            AgentResponseSanitizer responseSanitizer) {
        this.intentRouterService = intentRouterService;
        this.openRouterChatService = openRouterChatService;
        this.projectIssueToolService = projectIssueToolService;
        this.responseSanitizer = responseSanitizer;
    }

    public AgentChatResponse handle(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String documentContext,
            String conversationContext,
            String model) {
        AgentDecision decision = intentRouterService.route(request.question());
        if (decision.intent() == AgentIntent.ISSUE_ACTION || decision.intent() == AgentIntent.ISSUE_UPDATE) {
            return executeAction(userId, conversationId, request, authorization, decision, model);
        }
        if (decision.intent() == AgentIntent.ISSUE_QUERY || decision.intent() == AgentIntent.ISSUE_SEARCH) {
            return executeIssueQuery(conversationId, request, authorization, decision, model);
        }
        if (decision.intent() == AgentIntent.ISSUE_ANALYSIS
                || decision.intent() == AgentIntent.PROJECT_SUMMARY
                || decision.intent() == AgentIntent.DAILY_PLAN
                || decision.intent() == AgentIntent.RISK_ANALYSIS
                || decision.intent() == AgentIntent.SPRINT_REPORT
                || decision.intent() == AgentIntent.MEMBER_WORKLOAD
                || decision.intent() == AgentIntent.DEADLINE_CHECK) {
            return executeIssueAnalysis(conversationId, request, authorization, decision, model);
        }

        String enhancedQuestion = buildPromptForIntent(decision, request.question());
        String answer = responseSanitizer.sanitize(openRouterChatService.ask(enhancedQuestion,
                request.selectedDocumentIds(),
                documentContext,
                conversationContext));

        return new AgentChatResponse(
                answer,
                model,
                conversationId,
                Instant.now(),
                decision.intent().name(),
                decision.confidence(),
                Collections.emptyList(),
                buildSources(request.selectedDocumentIds(), request.projectId()));
    }

    private AgentChatResponse executeAction(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            AgentDecision decision,
            String model) {
        AgentProposedAction action = new AgentProposedAction(
                "ISSUE_UPDATE",
                "Thuc thi thao tac issue theo yeu cau nguoi dung",
                Map.of(
                        "projectId", request.projectId(),
                        "userInstruction", request.question()));

        return new AgentChatResponse(
                executeActionDirectly(request, action, authorization),
                model,
                conversationId,
                Instant.now(),
                decision.intent().name(),
                decision.confidence(),
                List.of(action),
                buildSources(request.selectedDocumentIds(), request.projectId()));
    }

    private AgentChatResponse executeIssueQuery(String conversationId,
            AgentChatRequest request,
            String authorization,
            AgentDecision decision,
            String model) {
        String answer = responseSanitizer.sanitize(projectIssueToolService.handleIssueQuery(request.question(), request.projectId(),
                authorization));
        return new AgentChatResponse(
                answer,
                model,
                conversationId,
                Instant.now(),
                decision.intent().name(),
                decision.confidence(),
                Collections.emptyList(),
                buildSources(request.selectedDocumentIds(), request.projectId()));
    }

    private AgentChatResponse executeIssueAnalysis(String conversationId,
            AgentChatRequest request,
            String authorization,
            AgentDecision decision,
            String model) {
        String answer = responseSanitizer.sanitize(projectIssueToolService.handleIssueAnalysis(request.question(), request.projectId(),
                authorization));
        return new AgentChatResponse(
                answer,
                model,
                conversationId,
                Instant.now(),
                decision.intent().name(),
                decision.confidence(),
                Collections.emptyList(),
                buildSources(request.selectedDocumentIds(), request.projectId()));
    }

    private String executeActionDirectly(AgentChatRequest request,
            AgentProposedAction action,
            String authorization) {
        String result = projectIssueToolService.handleIssueAction(request.question(), request.projectId(),
                authorization);
        return responseSanitizer.sanitize(result);
    }

    private String buildPromptForIntent(AgentDecision decision, String question) {
        return switch (decision.intent()) {
            case ISSUE_QUERY ->
                "Bạn là trợ lý quản lý dự án. Trả lời bằng tiếng Việt có dấu, ngắn gọn, không hiển thị UUID, projectId, internal id, raw JSON, endpoint, token hoặc stack trace.\n\nCâu hỏi: " + question;
            case ISSUE_ANALYSIS ->
                "Bạn là trợ lý phân tích dự án. Trả lời bằng tiếng Việt tự nhiên, chuyên nghiệp; chỉ dùng issue key, title, status, priority, assignee và due date khi cần. Không hiển thị dữ liệu kỹ thuật.\n\nCâu hỏi: "
                        + question;
            case SPRINT_SUMMARY ->
                "Bạn là trợ lý scrum. Tóm tắt tiến độ, blocker và 3 hành động ưu tiên bằng tiếng Việt có dấu. Không hiển thị dữ liệu kỹ thuật.\n\nCâu hỏi: "
                        + question;
            default -> question;
        };
    }

    private List<String> buildSources(List<String> selectedDocumentIds, String projectId) {
        String scope = projectId == null || projectId.isBlank() ? "unknown" : projectId;
        String docs = (selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                ? "all"
                : String.join(",", selectedDocumentIds);
        return List.of("project:" + scope, "documents:" + docs);
    }
}
