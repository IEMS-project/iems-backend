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
        assertTrue(result.contains("Ch\u01b0a ph\u00e2n lo\u1ea1i"));
        assertFalse(result.contains("550e8400"));
        assertFalse(result.contains("projectId"));
        assertFalse(result.contains("endpoint"));
        assertFalse(result.contains("ISSUE_UPDATE"));
    }

    @Test
    void sanitizeShouldRepairCollapsedMarkdownWithoutDroppingText() {
        String raw = "Ph\u1ea7n tr\u01b0\u1edbc v\u1eabn ph\u1ea3i c\u00f2n nguy\u00ean.*Sanitizer: "
                + "\u0110\u1ea3m b\u1ea3o x\u00f3a s\u1ea1ch ID nh\u01b0ng v\u1eabn gi\u1eef Markdown h\u1eefu \u00edch."
                + "T\u00f3m l\u1ea1i: \u0110\u00e2y l\u00e0 b\u1ea3n thi\u1ebft k\u1ebf n\u00e2ng c\u1ea5p AI.";

        String result = sanitizer.sanitize(raw);

        assertTrue(result.contains("Ph\u1ea7n tr\u01b0\u1edbc v\u1eabn ph\u1ea3i c\u00f2n nguy\u00ean"));
        assertTrue(result.contains("\n- Sanitizer: \u0110\u1ea3m b\u1ea3o"));
        assertTrue(result.contains("\n\nT\u00f3m l\u1ea1i: \u0110\u00e2y l\u00e0 b\u1ea3n thi\u1ebft k\u1ebf"));
    }
}
