package com.iems.aiservice.service;

import com.iems.aiservice.service.agent.AgentIntentRouterService;
import com.iems.aiservice.model.agent.AgentIntent;
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
    void routeShouldDetectIssueActionAnalysisSprintAndQuery() {
        assertEquals(AgentIntent.ISSUE_ACTION, service.route("cap nhat issue nay").intent());
        assertEquals(AgentIntent.ISSUE_ANALYSIS, service.route("phan tich rui ro cua task").intent());
        assertEquals(AgentIntent.SPRINT_SUMMARY, service.route("bao cao tien do sprint").intent());
        assertEquals(AgentIntent.ISSUE_QUERY, service.route("danh sach issue hom nay").intent());
    }

    @Test
    void routeShouldFallbackToGeneralChat() {
        assertEquals(AgentIntent.GENERAL_CHAT, service.route("hello there").intent());
    }
}