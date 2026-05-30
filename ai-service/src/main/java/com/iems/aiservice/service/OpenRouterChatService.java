package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class OpenRouterChatService {

    private final RestClient openRouterRestClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public OpenRouterChatService(RestClient openRouterRestClient,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.openRouterRestClient = openRouterRestClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt(resourceLoader, aiProperties.getSystemPromptFile());
    }

    public String ask(String question,
            List<String> selectedDocumentIds,
            String documentContext,
            String conversationContext) {
        ensureApiKeyConfigured();

        String scopedQuestion = buildScopedQuestion(question, selectedDocumentIds, documentContext,
                conversationContext);
        log.info("OpenRouter ask start model={} selectedCount={} contextChars={} memoryChars={} promptChars={}",
                aiProperties.getModel(),
                selectedDocumentIds == null ? 0 : selectedDocumentIds.size(),
                documentContext == null ? 0 : documentContext.length(),
                conversationContext == null ? 0 : conversationContext.length(),
                scopedQuestion.length());
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", scopedQuestion)),
                "temperature", aiProperties.getTemperature());

        Map<?, ?> response = openRouterRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String text = extractMessageContent(response);
        if (text != null && !text.isBlank()) {
            String repaired = repairVietnameseMarkdownIfNeeded(text.trim(), scopedQuestion);
            log.info("OpenRouter ask done answerChars={}", repaired.length());
            return repaired;
        }

        throw new IllegalStateException("Unable to parse answer from OpenRouter response");
    }

    public void streamAsk(String question,
            List<String> selectedDocumentIds,
            String documentContext,
            String conversationContext,
            Consumer<String> onChunk) {
        ensureApiKeyConfigured();

        String scopedQuestion = buildScopedQuestion(question, selectedDocumentIds, documentContext,
                conversationContext);
        log.info("OpenRouter stream start model={} selectedCount={} contextChars={} memoryChars={} promptChars={}",
                aiProperties.getModel(),
                selectedDocumentIds == null ? 0 : selectedDocumentIds.size(),
                documentContext == null ? 0 : documentContext.length(),
                conversationContext == null ? 0 : conversationContext.length(),
                scopedQuestion.length());
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", scopedQuestion)),
                "temperature", aiProperties.getTemperature());

        openRouterRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange((request, response) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || !line.startsWith("data:")) {
                                continue;
                            }

                            String data = line.substring("data:".length()).trim();
                            if ("[DONE]".equals(data)) {
                                log.info("OpenRouter stream done");
                                break;
                            }

                            JsonNode node = objectMapper.readTree(data);
                            JsonNode contentNode = node.path("choices").path(0).path("delta").path("content");
                            if (!contentNode.isMissingNode() && !contentNode.asText().isBlank()) {
                                onChunk.accept(contentNode.asText());
                            }
                        }
                    }
                    return null;
                });
    }

    public String describeImage(String fileName, String contentType, byte[] imageBytes) {
        ensureApiKeyConfigured();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Uploaded image is empty");
        }

        String mimeType = contentType == null || contentType.isBlank() ? "image/png" : contentType;
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        String prompt = "Hay doc va mo ta noi dung anh nay bang tieng Viet. "
                + "Neu anh co chu, bang bieu, giao dien, loi, so lieu hoac thong tin quan trong, hay trich xuat ro. "
                + "Ten file: " + (fileName == null ? "image" : fileName);

        Map<String, Object> payload = Map.of(
                "model", aiProperties.getVisionModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Ban la tro ly doc hinh anh va trich xuat thong tin de dung cho RAG."),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))),
                "temperature", aiProperties.getTemperature());

        Map<?, ?> response = openRouterRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String text = extractMessageContent(response);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Unable to parse image description from OpenRouter response");
        }
        return text.trim();
    }

    private String extractMessageContent(Map<?, ?> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }

        Object first = choices.get(0);
        if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            if (content instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private String repairVietnameseMarkdownIfNeeded(String answer, String originalPrompt) {
        if (!looksMostlyEnglish(answer)) {
            return answer;
        }

        log.info("OpenRouter answer looks English; repairing to Vietnamese Markdown");
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Ban chuyen cau tra loi sang tieng Viet tu nhien, dung Markdown, giu dung noi dung va cite neu co."),
                        Map.of("role", "user", "content",
                                "Ngu canh yeu cau ban dau:\n" + originalPrompt
                                        + "\n\nCau tra loi can sua:\n" + answer
                                        + "\n\nHay viet lai bang tieng Viet, Markdown de doc, khong them thong tin khong co.")),
                "temperature", 0.1);

        Map<?, ?> response = openRouterRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        String repaired = extractMessageContent(response);
        return repaired == null || repaired.isBlank() ? answer : repaired.trim();
    }

    private boolean looksMostlyEnglish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        int englishHits = countContains(lower,
                "the ", "and ", "to ", "of ", "with ", "this ", "that ", "document ", "workflow ",
                "approach ", "ensure ", "guidelines ", "based on ");
        int vietnameseHits = countContains(lower,
                " va ", " la ", " cua ", " trong ", " nguoi ", " tai lieu ", " du an ", " can ", " khong ",
                " file ", " giai thich ");
        return englishHits >= 3 && englishHits > vietnameseHits;
    }

    private int countContains(String text, String... needles) {
        int count = 0;
        for (String needle : needles) {
            if (text.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    private String buildScopedQuestion(String question,
            List<String> selectedDocumentIds,
            String documentContext,
            String conversationContext) {
        if ((selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                && (documentContext == null || documentContext.isBlank())
                && (conversationContext == null || conversationContext.isBlank())) {
            return question;
        }

        String scopeIds = (selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                ? "none"
                : String.join(", ", selectedDocumentIds);

        StringBuilder prompt = new StringBuilder();
        prompt.append("User question: ").append(question)
                .append("\n\nSelected document scope IDs: ").append(scopeIds)
                .append("\n\nInstruction:")
                .append("\n- BAT BUOC tra loi bang tieng Viet. Khong dung tieng Anh tru khi nguoi dung yeu cau.")
                .append("\n- Dinh dang cau tra loi bang Markdown de de doc.")
                .append("\n- Khong viet lien mot doan dai. Hay dung heading ngan, bullet points, va danh sach so neu phu hop.")
                .append("\n- Neu nguoi dung yeu cau 'dai ra', 'cu the hon', 'giai thich them', hay tra loi dai hon cau truoc it nhat 2-3 lan.")
                .append("\n- Neu co Document context, hay uu tien noi dung tai lieu va KHONG tra loi chung chung.")
                .append("\n- Neu cau hoi la follow-up ngan nhu 'cu the hon', 'noi dai ra', 'giai thich them', hay tiep tuc giai thich chu de dang noi trong Recent conversation memory.")
                .append("\n- Neu nguoi dung hoi 'file nay co gi', hay tom tat chi tiet theo cac muc: tong quan, cac y chinh, quy trinh/khai niem quan trong, vi du de hieu.")
                .append("\n- Neu tai lieu co it thong tin, noi ro phan nao suy luan tu ngu canh va phan nao lay tu tai lieu.")
                .append("\n- Khi dung noi dung tai lieu, cite marker dung dang [fileName #chunkIndex], vi du [git.docx #2].")
                .append("\n- Neu cau hoi mo ho nhung da co tai lieu/memory, tu chon cach giai thich huu ich nhat thay vi hoi lai ngay.")
                .append("\n\nOutput format bat buoc khi tra loi ve tai lieu:")
                .append("\n- Phai copy dung Markdown structure ben duoi.")
                .append("\n- Moi heading phai bat dau bang '## ' va phai co dong trong truoc/sau heading.")
                .append("\n- Moi bullet phai bat dau bang '- ' tren dong rieng.")
                .append("\n- Code lenh Git phai nam trong fenced code block ```bash.")
                .append("\n\n## Tom tat ngan\n")
                .append("\n- 2-4 y ngan gon.\n")
                .append("\n## Giai thich chi tiet\n")
                .append("\n- Giai thich tung y theo ngon ngu de hieu.\n")
                .append("\n## Vi du de hieu\n")
                .append("\n- Neu phu hop, dua vi du gan voi du an/lap trinh.\n")
                .append("\n## Ket luan\n")
                .append("\n- 1-2 cau chot lai.\n");

        if (documentContext != null && !documentContext.isBlank()) {
            prompt.append("\n\nDocument context:\n").append(documentContext);
        }

        if (conversationContext != null && !conversationContext.isBlank()) {
            prompt.append("\n\nRecent conversation memory:\n")
                    .append(conversationContext)
                    .append("\nUse this memory to keep continuity, but prioritize selected document context when relevant.");
        }

        return prompt.toString();
    }

    private String loadSystemPrompt(ResourceLoader resourceLoader, String promptPath) {
        String resolvedPath = (promptPath == null || promptPath.isBlank())
                ? "classpath:prompts/systemt_prompt.txt"
                : promptPath;
        try {
            Resource resource = resourceLoader.getResource(resolvedPath);
            if (!resource.exists()) {
                throw new IllegalStateException("System prompt file not found: " + resolvedPath);
            }
            try (var inputStream = resource.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (content.isBlank()) {
                    throw new IllegalStateException("System prompt file is empty: " + resolvedPath);
                }
                log.info("Loaded system prompt from {} (chars={})", resolvedPath, content.length());
                return content;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt file: " + resolvedPath, e);
        }
    }

    private void ensureApiKeyConfigured() {
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
        }
    }
}
