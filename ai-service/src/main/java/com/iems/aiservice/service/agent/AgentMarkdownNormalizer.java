package com.iems.aiservice.service.agent;

public final class AgentMarkdownNormalizer {

    /**
     * Creates a new agent markdown service instance.
     */
    private AgentMarkdownNormalizer() {
    }

    /**
     * Normalizes agent markdown content.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param text the text parameter
     * @return the normalize result
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u200B", "")
                .replace("\uFEFF", "");

        normalized = normalized
                .replaceAll("(?m)^\\s*[.•]\\s+", "- ")
                .replaceAll("(?m)^\\s*\\.\\s*\\*\\*", "- **")
                .replaceAll("\\.\\s*\\*([A-Z0-9À-Ỵ][^:\\n]{1,80}:)", "\n- $1")
                .replaceAll("(?<!\\n)(#{1,6}\\s+)", "\n\n$1")
                .replaceAll("(?<!\\n)([-*+]\\s+)(?=\\*\\*|[A-Z0-9À-Ỵ])", "\n$1")
                .replaceAll("(?<!\\n)(\\d+\\.\\s+)(?=\\S)", "\n$1")
                .replaceAll("(?<=[.!?])(?=\\*\\*[^*\\n]+\\**:)", "\n\n")
                .replaceAll("(?<=[.!?])(?=(Tóm lại|Kết luận|Lưu ý|Gợi ý|Ví dụ|Chi tiết):)", "\n\n")
                .replaceAll("(?m)^([*-]\\s+\\*\\*[^*\\n]+\\*\\*:)", "\n$1")
                .replaceAll("(?m)^(#{1,6}\\s+.+)\\n(?!\\n)", "$1\n\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return normalized
                .replaceAll("(?i)Tom tat ngan", "T\u00f3m t\u1eaft ng\u1eafn")
                .replaceAll("(?i)Giai thich chi tiet", "Gi\u1ea3i th\u00edch chi ti\u1ebft")
                .replaceAll("(?i)Giai thiet chi tiet", "Gi\u1ea3i th\u00edch chi ti\u1ebft")
                .replaceAll("(?i)Vi du de hieu", "V\u00ed d\u1ee5 d\u1ec5 hi\u1ec3u")
                .replaceAll("(?i)Ket luan", "K\u1ebft lu\u1eadn");
    }
}
