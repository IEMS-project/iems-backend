package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentAction;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.model.agent.AgentPlan;
import com.iems.aiservice.model.agent.PendingAgentAction;
import com.iems.aiservice.service.OpenRouterChatService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AgentPipelineService {

    private final AgentInputSanitizer inputSanitizer;
    private final AgentPlannerService plannerService;
    private final ProjectToolExecutor projectToolExecutor;
    private final PendingActionStore pendingActionStore;
    private final OpenRouterChatService openRouterChatService;
    private final AgentResponseSanitizer responseSanitizer;

    public AgentPipelineService(AgentInputSanitizer inputSanitizer,
            AgentPlannerService plannerService,
            ProjectToolExecutor projectToolExecutor,
            PendingActionStore pendingActionStore,
            OpenRouterChatService openRouterChatService,
            AgentResponseSanitizer responseSanitizer) {
        this.inputSanitizer = inputSanitizer;
        this.plannerService = plannerService;
        this.projectToolExecutor = projectToolExecutor;
        this.pendingActionStore = pendingActionStore;
        this.openRouterChatService = openRouterChatService;
        this.responseSanitizer = responseSanitizer;
    }

    public AgentChatResponse handle(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String documentContext,
            String conversationContext,
            String model) {
        String sanitizedQuestion = inputSanitizer.sanitizeQuestion(request.question());
        AgentChatRequest sanitizedRequest = new AgentChatRequest(
                sanitizedQuestion,
                conversationId,
                request.projectId(),
                request.selectedDocumentIds());
        AgentPlan plan = plannerService.plan(userId, conversationId, sanitizedRequest, conversationContext);

        return switch (plan.action()) {
            case ANSWER -> answerWithOpenRouter(conversationId, sanitizedRequest,
                    documentContext, conversationContext, model, plan);
            case READ_PROJECT -> answerWithProjectData(userId, conversationId, sanitizedRequest,
                    authorization, model, plan);
            case PROPOSE_WRITE -> proposeWrite(userId, conversationId, sanitizedRequest,
                    authorization, model, plan);
            case EXECUTE_CONFIRMED_WRITE -> executeConfirmedWrite(userId, conversationId, sanitizedRequest,
                    authorization, model, plan);
            case CLARIFY -> response(conversationId, model, plan,
                    plan.naturalLanguageHint().isBlank()
                            ? "Mình cần thêm thông tin để xử lý yêu cầu này."
                            : plan.naturalLanguageHint(),
                    List.of());
        };
    }

    public AgentChatResponse confirmAction(String userId,
            String conversationId,
            String actionId,
            String projectId,
            String authorization,
            String model) {
        Optional<PendingAgentAction> pendingAction = pendingActionStore.find(conversationId, userId);
        if (pendingAction.isEmpty()) {
            AgentPlan clarify = AgentPlan.clarify(
                    com.iems.aiservice.model.agent.AgentIntent.ISSUE_UPDATE,
                    "Mình chưa thấy thao tác nào đang chờ xác nhận. Bạn gửi lại yêu cầu cập nhật cụ thể nhé.",
                    List.of("pendingAction"));
            return response(conversationId, model, clarify, clarify.naturalLanguageHint(), List.of());
        }

        PendingAgentAction action = pendingAction.get();
        if (!action.actionId().equals(actionId)) {
            AgentPlan clarify = AgentPlan.clarify(
                    com.iems.aiservice.model.agent.AgentIntent.ISSUE_UPDATE,
                    "Mã xác nhận không khớp với thao tác đang chờ. Bạn thử tạo lại yêu cầu cập nhật nhé.",
                    List.of("actionId"));
            return response(conversationId, model, clarify, clarify.naturalLanguageHint(), List.of());
        }

        AgentToolResult result;
        try {
            result = projectToolExecutor.executeConfirmedWrite(action, authorization);
        } catch (ProjectToolExecutor.AgentWriteException ex) {
            result = AgentToolResult.error(ex.getMessage());
        }
        if (result.success()) {
            pendingActionStore.consume(conversationId, userId);
        }
        List<AgentProposedAction> proposedActions = result.success()
                ? result.proposedActions()
                : List.of(new AgentProposedAction(action.toolName(), "Thu lai", action.payload()));
        AgentPlan plan = new AgentPlan(
                AgentAction.EXECUTE_CONFIRMED_WRITE,
                com.iems.aiservice.model.agent.AgentIntent.ISSUE_UPDATE,
                result.success() ? 0.98 : 0.75,
                action.toolName(),
                List.of("actionId"),
                java.util.Map.of("projectId", projectId == null ? "" : projectId),
                List.of(),
                "Execute explicitly allowed action.",
                false,
                "");
        return response(conversationId, model, plan, result.answer(), proposedActions);
    }

    private AgentChatResponse answerWithOpenRouter(String conversationId,
            AgentChatRequest request,
            String documentContext,
            String conversationContext,
            String model,
            AgentPlan plan) {
        String answer = openRouterChatService.ask(
                request.question(),
                request.selectedDocumentIds(),
                documentContext,
                conversationContext);
        return response(conversationId, model, plan, answer, List.of());
    }

    private AgentChatResponse answerWithProjectData(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String model,
            AgentPlan plan) {
        AgentToolResult result = projectToolExecutor.readProject(plan, request, userId, authorization);
        return response(conversationId, model, plan, result.answer(), result.proposedActions());
    }

    private AgentChatResponse proposeWrite(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String model,
            AgentPlan plan) {
        AgentToolResult result = projectToolExecutor.proposeWrite(plan, userId, conversationId, authorization);
        AgentAction action = result.success() ? AgentAction.PROPOSE_WRITE : AgentAction.CLARIFY;
        AgentPlan effectivePlan = result.success()
                ? plan
                : new AgentPlan(action, plan.intent(), plan.confidence(), plan.targetTool(), plan.requiredInputs(),
                        plan.resolvedInputs(), plan.missingInputs(), plan.expectedOutput(), false, result.answer());
        return response(conversationId, model, effectivePlan, result.answer(), result.proposedActions());
    }

    private AgentChatResponse executeConfirmedWrite(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String model,
            AgentPlan plan) {
        Optional<PendingAgentAction> pendingAction = pendingActionStore.consume(conversationId, userId);
        if (pendingAction.isEmpty()) {
            AgentPlan clarify = AgentPlan.clarify(
                    plan.intent(),
                    "Mình chưa thấy thao tác nào đang chờ xác nhận. Bạn gửi lại yêu cầu cập nhật cụ thể nhé.",
                    List.of("pendingAction"));
            return response(conversationId, model, clarify, clarify.naturalLanguageHint(), List.of());
        }

        AgentToolResult result;
        try {
            result = projectToolExecutor.executeConfirmedWrite(pendingAction.get(), authorization);
        } catch (ProjectToolExecutor.AgentWriteException ex) {
            result = AgentToolResult.error(ex.getMessage());
        }
        if (!result.success()) {
            return response(conversationId, model, plan, result.answer(), List.of());
        }
        return response(conversationId, model, plan, result.answer(), result.proposedActions());
    }

    private AgentChatResponse response(String conversationId,
            String model,
            AgentPlan plan,
            String answer,
            List<com.iems.aiservice.model.agent.AgentProposedAction> actions) {
        return new AgentChatResponse(
                responseSanitizer.sanitize(answer),
                model,
                conversationId,
                Instant.now(),
                plan.intent().name(),
                plan.confidence(),
                actions,
                List.of("project:" + (plan.resolvedInputs().containsKey("projectId") ? "current" : "unknown")));
    }
}
