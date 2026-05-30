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

    public AgentOrchestratorService(AgentIntentRouterService intentRouterService,
            OpenRouterChatService openRouterChatService,
            ProjectIssueToolService projectIssueToolService) {
        this.intentRouterService = intentRouterService;
        this.openRouterChatService = openRouterChatService;
        this.projectIssueToolService = projectIssueToolService;
    }

    public AgentChatResponse handle(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String documentContext,
            String conversationContext,
            String model) {
        AgentDecision decision = intentRouterService.route(request.question());
        if (decision.intent() == AgentIntent.ISSUE_ACTION) {
            return executeAction(userId, conversationId, request, authorization, decision, model);
        }
        if (decision.intent() == AgentIntent.ISSUE_QUERY) {
            return executeIssueQuery(conversationId, request, authorization, decision, model);
        }
        if (decision.intent() == AgentIntent.ISSUE_ANALYSIS) {
            return executeIssueAnalysis(conversationId, request, authorization, decision, model);
        }

        String enhancedQuestion = buildPromptForIntent(decision, request.question());
        String answer = openRouterChatService.ask(enhancedQuestion,
                request.selectedDocumentIds(),
                documentContext,
                conversationContext);

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
                executeActionDirectly(userId, request, action, authorization),
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
        String answer = projectIssueToolService.handleIssueQuery(request.question(), request.projectId(),
                authorization);
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
        String answer = projectIssueToolService.handleIssueAnalysis(request.question(), request.projectId(),
                authorization);
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

    private String executeActionDirectly(String userId,
            AgentChatRequest request,
            AgentProposedAction action,
            String authorization) {
        String result = projectIssueToolService.handleIssueAction(request.question(), request.projectId(),
                authorization);
        return "User " + userId + " -> " + action.type() + "\n" + result;
    }

    private String buildPromptForIntent(AgentDecision decision, String question) {
        return switch (decision.intent()) {
            case ISSUE_QUERY ->
                "Ban la tro ly issue tracker. Tra loi ngan gon theo du lieu issue/task.\n\nUser query: " + question;
            case ISSUE_ANALYSIS ->
                "Ban la tro ly phan tich issue. Hay dua ra nguyen nhan kha di, rui ro, de xuat hanh dong tiep theo va muc uu tien.\n\nUser query: "
                        + question;
            case SPRINT_SUMMARY ->
                "Ban la tro ly scrum. Hay tom tat sprint/worklog, blocker, va de xuat 3 hanh dong uu tien.\n\nUser query: "
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
