package com.iems.aiservice.service;

import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import org.junit.jupiter.api.Test;

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
    void routeShouldNotTreatReportsAsUpdate() {
        assertEquals(AgentIntent.PROJECT_SUMMARY, service.route("tóm tắt tiến độ").intent());
        assertEquals(AgentIntent.PROJECT_SUMMARY, service.route("đếm số issue theo status và priority").intent());
        assertEquals(AgentIntent.DAILY_PLAN, service.route("lập kế hoạch hôm nay").intent());
        assertEquals(AgentIntent.ISSUE_UPDATE, service.route("Chuyển task này sang Done").intent());
    }

    @Test
    void routeShouldFallbackToGeneralChat() {
        assertEquals(AgentIntent.GENERAL_CHAT, service.route("hello there").intent());
    }
}
