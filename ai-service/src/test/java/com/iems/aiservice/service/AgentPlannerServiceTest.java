package com.iems.aiservice.service;

import com.iems.aiservice.dto.AgentChatRequest;
import com.iems.aiservice.model.agent.AgentAction;
import com.iems.aiservice.model.agent.AgentIntent;
import com.iems.aiservice.model.agent.AgentPlan;
import com.iems.aiservice.model.agent.PendingAgentAction;
import com.iems.aiservice.service.agent.AgentIntentRouterService;
import com.iems.aiservice.service.agent.AgentPlannerService;
import com.iems.aiservice.service.agent.PendingActionStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPlannerServiceTest {

    private final PendingActionStore pendingActionStore = new PendingActionStore();
    private final AgentPlannerService service = new AgentPlannerService(
            new AgentIntentRouterService(),
            pendingActionStore);

    @Test
    void updateStatusShouldBecomeProposeWrite() {
        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("Cap nhat IEMS2-8 sang Done", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.PROPOSE_WRITE, plan.action());
        assertEquals(AgentIntent.ISSUE_UPDATE, plan.intent());
        assertEquals("update_issue_status", plan.targetTool());
        assertEquals("IEMS2-8", plan.resolvedInputs().get("issueKey"));
        assertEquals("Done", plan.resolvedInputs().get("targetStatus"));
    }

    @Test
    void confirmationWithoutPendingActionShouldClarify() {
        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("dung roi", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.CLARIFY, plan.action());
    }

    @Test
    void confirmationWithPendingActionShouldAskForAllowButton() {
        Instant now = Instant.now();
        pendingActionStore.save(new PendingAgentAction(
                "action-1",
                "conv-1",
                "user-1",
                "project-1",
                "update_issue_status",
                Map.of("issueId", "issue-8", "targetStatusId", "done"),
                "Update IEMS2-8",
                now,
                now.plus(PendingActionStore.DEFAULT_TTL)));

        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("ok cap nhat di", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.CLARIFY, plan.action());
        assertEquals(List.of("allowAction"), plan.missingInputs());
    }

    @Test
    void dailyPlanShouldReadProject() {
        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("lap ke hoach hom nay", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.READ_PROJECT, plan.action());
        assertEquals(AgentIntent.DAILY_PLAN, plan.intent());
    }

    @Test
    void createIssueShouldBecomeCreateToolInsteadOfAskingForIssueKey() {
        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("tao issue User Login", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.PROPOSE_WRITE, plan.action());
        assertEquals("create_issue", plan.targetTool());
    }

    @Test
    void generalChatShouldAnswer() {
        AgentPlan plan = service.plan(
                "user-1",
                "conv-1",
                new AgentChatRequest("hello", "conv-1", "project-1", List.of()),
                "");

        assertEquals(AgentAction.ANSWER, plan.action());
        assertEquals(AgentIntent.GENERAL_CHAT, plan.intent());
    }
}
