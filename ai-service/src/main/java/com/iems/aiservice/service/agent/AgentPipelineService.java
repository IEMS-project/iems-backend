package com.iems.aiservice.service.agent;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentAction;
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

    /**
     * Creates a new agent pipeline service instance.
     *
     * @param inputSanitizer the input sanitizer parameter
     * @param plannerService the planner service parameter
     * @param projectToolExecutor the project tool executor parameter
     * @param pendingActionStore the pending action store parameter
     * @param openRouterChatService the open router chat service parameter
     * @param responseSanitizer the response sanitizer parameter
     */
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

    /**
     * Handles the agent pipeline operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @param documentContext the document context parameter
     * @param conversationContext the conversation context parameter
     * @param model the model parameter
     * @return the handle result
     */
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

    /**
     * Returns confirm action for agent pipeline processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Load the domain data required for the operation.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param actionId the action id parameter
     * @param projectId the project id parameter
     * @param authorization the authorization parameter
     * @param model the model parameter
     * @return the confirm action result
     */
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

        pendingActionStore.consume(conversationId, userId);
        AgentToolResult result;
        try {
            result = projectToolExecutor.executeConfirmedWrite(action, authorization);
        } catch (ProjectToolExecutor.AgentWriteException ex) {
            result = AgentToolResult.error(ex.getMessage());
        }
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
        return response(conversationId, model, plan, result.answer(), result.success() ? result.proposedActions() : List.of());
    }

    /**
     * Returns answer with open router for agent pipeline processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param documentContext the document context parameter
     * @param conversationContext the conversation context parameter
     * @param model the model parameter
     * @param plan the plan parameter
     * @return the answer with open router result
     */
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

    /**
     * Returns answer with project data for agent pipeline processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @param model the model parameter
     * @param plan the plan parameter
     * @return the answer with project data result
     */
    private AgentChatResponse answerWithProjectData(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String model,
            AgentPlan plan) {
        AgentToolResult result = projectToolExecutor.readProject(plan, request, userId, authorization);
        return response(conversationId, model, plan, result.answer(), result.proposedActions());
    }

    /**
     * Returns propose write for agent pipeline processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @param model the model parameter
     * @param plan the plan parameter
     * @return the propose write result
     */
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

    /**
     * Executes the agent pipeline operation.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param userId the user id parameter
     * @param conversationId the conversation id parameter
     * @param request the request parameter
     * @param authorization the authorization parameter
     * @param model the model parameter
     * @param plan the plan parameter
     * @return the execute confirmed write result
     */
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

    /**
     * Returns response for agent pipeline processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param conversationId the conversation id parameter
     * @param model the model parameter
     * @param plan the plan parameter
     * @param answer the answer parameter
     * @param actions the actions parameter
     * @return the response result
     */
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
