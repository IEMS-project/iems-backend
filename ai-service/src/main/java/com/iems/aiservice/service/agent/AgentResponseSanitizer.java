package com.iems.aiservice.service.agent;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AgentResponseSanitizer {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern TECHNICAL_FIELD_LINE_PATTERN = Pattern.compile(
            "(?im)^.*\\b(token|endpoint|api|stack trace)\\b.*$\\R?");
    private static final Pattern INLINE_TECHNICAL_FIELD_PATTERN = Pattern.compile(
            "(?i)\\s*\\|?\\s*(id|projectId|project_id|issueId|internalId|token|endpoint|api)\\s*=\\s*[^|\\n]+");
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```\\s*json\\s*.*?```");

    public String sanitize(String answer) {
        if (answer == null || answer.isBlank()) {
            return "M\u00ecnh ch\u01b0a c\u00f3 \u0111\u1ee7 d\u1eef li\u1ec7u \u0111\u1ec3 tr\u1ea3 l\u1eddi. B\u1ea1n th\u1eed l\u1ea1i sau v\u00e0i gi\u00e2y nh\u00e9.";
        }

        String sanitized = answer;
        sanitized = JSON_BLOCK_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = TECHNICAL_FIELD_LINE_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = INLINE_TECHNICAL_FIELD_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = UUID_PATTERN.matcher(sanitized).replaceAll("[\u0111\u00e3 \u1ea9n]");
        sanitized = sanitized
                .replace("unknown", "Ch\u01b0a ph\u00e2n lo\u1ea1i")
                .replace("Unknown", "Ch\u01b0a ph\u00e2n lo\u1ea1i")
                .replace("null", "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u")
                .replace("undefined", "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u")
                .replaceAll("(?m)^User\\s+.*?->\\s*ISSUE_UPDATE\\s*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        sanitized = AgentMarkdownNormalizer.normalize(sanitized);

        return sanitized.isBlank()
                ? "M\u00ecnh \u0111\u00e3 x\u1eed l\u00fd xong, nh\u01b0ng kh\u00f4ng c\u00f3 n\u1ed9i dung ph\u00f9 h\u1ee3p \u0111\u1ec3 hi\u1ec3n th\u1ecb."
                : sanitized;
    }
}
