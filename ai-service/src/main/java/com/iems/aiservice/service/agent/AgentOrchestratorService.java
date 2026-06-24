package com.iems.aiservice.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.dto.AgentChatResponse;
import com.iems.aiservice.model.agent.AgentDecision;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.model.agent.AgentProposedAction;
import com.iems.aiservice.service.OpenRouterChatService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentOrchestratorService {

    private final AgentIntentRouterService intentRouterService;
    private final OpenRouterChatService openRouterChatService;
    private final ProjectIssueToolService projectIssueToolService;
    private final AgentResponseSanitizer responseSanitizer;
    private final ObjectMapper objectMapper;

    public AgentOrchestratorService(AgentIntentRouterService intentRouterService,
            OpenRouterChatService openRouterChatService,
            ProjectIssueToolService projectIssueToolService,
            AgentResponseSanitizer responseSanitizer,
            ObjectMapper objectMapper) {
        this.intentRouterService = intentRouterService;
        this.openRouterChatService = openRouterChatService;
        this.projectIssueToolService = projectIssueToolService;
        this.responseSanitizer = responseSanitizer;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse handle(String userId,
            String conversationId,
            AgentChatRequest request,
            String authorization,
            String documentContext,
            String conversationContext,
            String model) {
        AgentDecision decision = intentRouterService.route(
                request.question(),
                request.projectId(),
                request.selectedDocumentIds(),
                conversationContext);

        if (decision.intent() == AgentIntent.ISSUE_ACTION || decision.intent() == AgentIntent.ISSUE_UPDATE) {
            return executeAction(conversationId, request, decision, model);
        }

        String groundedQuestion = buildGroundedAgentPrompt(request, authorization, decision, documentContext,
                conversationContext);
        String answer = responseSanitizer.sanitize(openRouterChatService.ask(
                groundedQuestion,
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

    private AgentChatResponse executeAction(String conversationId,
            AgentChatRequest request,
            AgentDecision decision,
            String model) {
        AgentProposedAction action = new AgentProposedAction(
                "ISSUE_UPDATE",
                "Đề xuất thao tác issue theo yêu cầu người dùng",
                Map.of(
                        "projectScope", "current_project",
                        "userInstruction", request.question()));

        return new AgentChatResponse(
                buildSafeActionConfirmation(request, action),
                model,
                conversationId,
                Instant.now(),
                decision.intent().name(),
                decision.confidence(),
                List.of(action),
                buildSources(request.selectedDocumentIds(), request.projectId()));
    }

    private String buildSafeActionConfirmation(AgentChatRequest request, AgentProposedAction action) {
        String projectText = request.projectId() == null || request.projectId().isBlank()
                ? "dự án hiện tại"
                : "dự án đang mở";
        return responseSanitizer.sanitize("""
                Mình hiểu đây là yêu cầu thay đổi dữ liệu nên sẽ chưa tự thực hiện ngay.

                Mình đã chuẩn bị một hành động cần xác nhận:
                - Loại thao tác: cập nhật issue
                - Phạm vi: %s
                - Yêu cầu: %s

                Bạn xác nhận trên giao diện trước khi mình gửi thay đổi sang project service nhé.
                """.formatted(projectText, action.payload().getOrDefault("userInstruction", request.question())));
    }

    private String buildGroundedAgentPrompt(AgentChatRequest request,
            String authorization,
            AgentDecision decision,
            String documentContext,
            String conversationContext) {
        Map<String, Object> projectFacts = resolveProjectFacts(request, authorization, decision);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là Project Copilot của IEMS, trả lời như một teammate PM/dev đang hỗ trợ thật.\n")
                .append("\nCâu hỏi hiện tại:\n")
                .append(request.question())
                .append("\n\nIntent dự đoán: ")
                .append(decision.intent().name())
                .append(" (confidence ")
                .append(decision.confidence())
                .append(").\n\nCách trả lời:")
                .append("\n- Luôn trả lời bằng tiếng Việt tự nhiên, có dấu.")
                .append("\n- Trả lời đúng câu hỏi hiện tại; câu đơn giản thì trả lời gọn, câu phân tích thì có cấu trúc.")
                .append("\n- Ưu tiên dữ liệu thật trong Project facts, Document context và Recent conversation.")
                .append("\n- Nếu phải suy luận, nói rõ đó là nhận định/suy luận từ dữ liệu hiện có.")
                .append("\n- Không lộ UUID, internal id, projectId, raw JSON, endpoint, token hoặc stack trace.")
                .append("\n- Không ép template cứng. Chỉ dùng heading/bullet khi giúp dễ đọc.")
                .append("\n- Nếu thiếu dữ liệu để trả lời chắc chắn, nói thiếu gì và gợi ý câu hỏi/hành động tiếp theo.");

        if (documentContext != null && !documentContext.isBlank()) {
            prompt.append("\n- Khi dùng tài liệu, cite marker dạng [fileName #chunkIndex] nếu context có marker.");
        }

        if (!projectFacts.isEmpty()) {
            prompt.append("\n\nProject facts for grounding (do not expose as raw JSON):\n")
                    .append(toJson(projectFacts));
        } else if (request.projectId() == null || request.projectId().isBlank()) {
            prompt.append("\n\nProject facts: chưa có projectId trong request. Nếu người dùng hỏi về dự án, hãy hỏi họ chọn dự án trước.");
        }

        if (conversationContext != null && !conversationContext.isBlank()) {
            prompt.append("\n\nRecent conversation memory:\n")
                    .append(conversationContext);
        }

        if (documentContext != null && !documentContext.isBlank()) {
            prompt.append("\n\nDocument context is available in the surrounding request. Use it before general knowledge.");
        }

        return prompt.toString();
    }

    private Map<String, Object> resolveProjectFacts(AgentChatRequest request,
            String authorization,
            AgentDecision decision) {
        if (request.projectId() == null || request.projectId().isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("intent", decision.intent().name());
        facts.put("projectScope", "current_project");

        try {
            facts.put("overview", projectIssueToolService.getProjectOverview(request.projectId(), authorization));
        } catch (Exception ex) {
            facts.put("overviewError", "Không lấy được tổng quan dự án lúc này.");
        }

        try {
            if (decision.intent() == AgentIntent.RISK_ANALYSIS
                    || decision.intent() == AgentIntent.DEADLINE_CHECK
                    || decision.intent() == AgentIntent.PROJECT_SUMMARY
                    || decision.intent() == AgentIntent.CONTEXTUAL_PROJECT_CHAT) {
                facts.put("riskSignals", projectIssueToolService.getRiskSignals(request.projectId(), authorization));
            }
        } catch (Exception ex) {
            facts.put("riskSignalsError", "Không lấy được tín hiệu rủi ro lúc này.");
        }

        try {
            if (decision.intent() == AgentIntent.MEMBER_WORKLOAD
                    || decision.intent() == AgentIntent.PROJECT_SUMMARY
                    || decision.intent() == AgentIntent.CONTEXTUAL_PROJECT_CHAT) {
                facts.put("memberWorkload", projectIssueToolService.getMemberWorkload(request.projectId(), authorization));
            }
        } catch (Exception ex) {
            facts.put("memberWorkloadError", "Không lấy được workload lúc này.");
        }

        try {
            if (decision.intent() == AgentIntent.ISSUE_SEARCH
                    || decision.intent() == AgentIntent.ISSUE_QUERY
                    || decision.intent() == AgentIntent.DAILY_PLAN
                    || decision.intent() == AgentIntent.SPRINT_REPORT
                    || decision.intent() == AgentIntent.CONTEXTUAL_PROJECT_CHAT) {
                facts.put("issues", projectIssueToolService.getProjectIssues(request.projectId(), authorization));
            }
        } catch (Exception ex) {
            facts.put("issuesError", "Không lấy được danh sách issue lúc này.");
        }

        return facts;
    }

    private String toJson(Map<String, Object> facts) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            return facts.toString();
        }
    }

    private List<String> buildSources(List<String> selectedDocumentIds, String projectId) {
        String scope = projectId == null || projectId.isBlank() ? "unknown" : "current";
        String docs = (selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                ? "all"
                : String.join(",", selectedDocumentIds);
        return List.of("project:" + scope, "documents:" + docs);
    }
}
