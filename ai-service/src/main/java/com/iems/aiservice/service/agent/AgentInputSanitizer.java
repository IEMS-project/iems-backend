package com.iems.aiservice.service.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentInputSanitizer {

    private static final int MAX_QUESTION_CHARS = 4_000;

    /**
     * Sanitizes agent input input.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param question the question parameter
     * @return the sanitize question result
     */
    public String sanitizeQuestion(String question) {
        if (question == null) {
            return "";
        }

        String cleaned = question
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() <= MAX_QUESTION_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_QUESTION_CHARS).trim();
    }
}
