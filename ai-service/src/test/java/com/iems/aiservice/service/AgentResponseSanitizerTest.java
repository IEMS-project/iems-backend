package com.iems.aiservice.service;

import com.iems.aiservice.service.agent.AgentResponseSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResponseSanitizerTest {

    private final AgentResponseSanitizer sanitizer = new AgentResponseSanitizer();

    @Test
    void sanitizeShouldHideTechnicalIdentifiers() {
        String raw = """
                User 123 -> ISSUE_UPDATE
                IEMS2-5 | Login bug | status=unknown | priority=High | id=550e8400-e29b-41d4-a716-446655440000 | projectId=abc
                endpoint=/api/projects
                """;

        String result = sanitizer.sanitize(raw);

        assertTrue(result.contains("IEMS2-5"));
        assertTrue(result.contains("Chưa phân loại"));
        assertFalse(result.contains("550e8400"));
        assertFalse(result.contains("projectId"));
        assertFalse(result.contains("endpoint"));
        assertFalse(result.contains("ISSUE_UPDATE"));
    }
}
