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
            return "Mình chưa có đủ dữ liệu để trả lời. Bạn thử lại sau vài giây nhé.";
        }

        String sanitized = answer;
        sanitized = JSON_BLOCK_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = TECHNICAL_FIELD_LINE_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = INLINE_TECHNICAL_FIELD_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = UUID_PATTERN.matcher(sanitized).replaceAll("[đã ẩn]");
        sanitized = sanitized
                .replace("unknown", "Chưa phân loại")
                .replace("Unknown", "Chưa phân loại")
                .replace("null", "Chưa có dữ liệu")
                .replace("undefined", "Chưa có dữ liệu")
                .replaceAll("(?m)^User\\s+.*?->\\s*ISSUE_UPDATE\\s*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return sanitized.isBlank()
                ? "Mình đã xử lý xong, nhưng không có nội dung phù hợp để hiển thị."
                : sanitized;
    }
}
