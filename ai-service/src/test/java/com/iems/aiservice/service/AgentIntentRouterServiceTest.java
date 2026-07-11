package com.iems.aiservice.service;

import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntentRouterServiceTest {

    private final AgentIntentRouterService service = new AgentIntentRouterService();

    @Test
    void routeShouldFallbackForEmptyQuestion() {
        assertEquals(AgentIntent.GENERAL_CHAT, service.route(null).intent());
        assertEquals(AgentIntent.GENERAL_CHAT, service.route("   ").intent());
    }

    @Test
    void routeShouldDetectProjectCopilotIntents() {
        assertEquals(AgentIntent.ISSUE_UPDATE, service.route("Chuyển IEMS2-5 sang Done").intent());
        assertEquals(AgentIntent.ISSUE_UPDATE, service.route("gán IEMS2-12 cho Nguyễn Văn A").intent());
        assertEquals(AgentIntent.RISK_ANALYSIS, service.route("phân tích rủi ro của sprint").intent());
        assertEquals(AgentIntent.PROJECT_SUMMARY, service.route("tóm tắt tiến độ dự án").intent());
        assertEquals(AgentIntent.DAILY_PLAN, service.route("lập kế hoạch hôm nay với 5 việc ưu tiên").intent());
        assertEquals(AgentIntent.MEMBER_WORKLOAD, service.route("Ai đang quá tải?").intent());
        assertEquals(AgentIntent.ISSUE_SEARCH, service.route("danh sách issue hôm nay").intent());
    }

    @Test
    void routeShouldDetectNaturalVietnameseProjectQuestions() {
        assertEquals(AgentIntent.DAILY_PLAN, service.route("hom nay toi can lam gi").intent());
        assertEquals(AgentIntent.DAILY_PLAN, service.route("viec cua toi hom nay").intent());
        assertEquals(AgentIntent.ISSUE_SEARCH, service.route("task cua toi").intent());
        assertEquals(AgentIntent.SPRINT_REPORT, service.route("sprint nay the nao").intent());
        assertEquals(AgentIntent.ISSUE_UPDATE, service.route("chuyen IEMS-1 sang Done").intent());
    }

    @Test
    void routeShouldUseContextForFollowUpsAndProjectQuestions() {
        assertEquals(AgentIntent.CONTEXTUAL_PROJECT_CHAT,
                service.route("nói rõ hơn", "project-1", List.of(), "Assistant: Dự án có rủi ro deadline.").intent());
        assertEquals(AgentIntent.CONTEXTUAL_PROJECT_CHAT,
                service.route("hôm nay nên làm gì?", "project-1", List.of(), "").intent());
        assertEquals(AgentIntent.DOCUMENT_QA,
                service.route("file này có gì?", "project-1", List.of("doc-1"), "").intent());
    }

    @Test
    void routeShouldNotTreatReportsAsUpdate() {
        assertEquals(AgentIntent.PROJECT_SUMMARY, service.route("tóm tắt tiến độ").intent());
        assertEquals(AgentIntent.PROJECT_SUMMARY, service.route("đếm số issue theo status và priority").intent());
        assertEquals(AgentIntent.DAILY_PLAN, service.route("lập kế hoạch hôm nay").intent());
        assertEquals(AgentIntent.ISSUE_UPDATE, service.route("Chuyển task này sang Done").intent());
    }

    @Test
    void routeShouldHandleAccentedVietnameseManagementQuestions() {
        assertEquals(AgentIntent.PROJECT_SUMMARY,
                service.route("Tóm tắt tình trạng dự án này bằng tiếng Việt tự nhiên, nêu rõ số lượng task theo trạng thái nếu có.").intent());
        assertEquals(AgentIntent.DAILY_PLAN,
                service.route("Hôm nay team nên ưu tiên 5 việc nào để giảm rủi ro nhất? Giải thích ngắn gọn.").intent());
    }

    @Test
    void routeShouldFallbackToGeneralChat() {
        assertEquals(AgentIntent.GENERAL_CHAT, service.route("hello there").intent());
        assertEquals(AgentIntent.GENERAL_CHAT,
                service.route("hello ban co the giup gi cho minh vay?", "project-1", List.of(), "Assistant: phan tich rui ro sprint").intent());
    }
}
