package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.config.AiProperties;
import com.iems.aiservice.service.agent.AgentMarkdownNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        if (isSmallTalkOrCapabilityQuestion(question, documentContext)) {
            return capabilityAnswer();
        }
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

        Map<?, ?> response = postJsonWithKeyRotation("/chat/completions", payload);

        String text = extractMessageContent(response);
        if (text != null && !text.isBlank()) {
            String repaired = normalizeMarkdown(repairVietnameseMarkdownIfNeeded(text.trim(), scopedQuestion));
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
        if (isSmallTalkOrCapabilityQuestion(question, documentContext)) {
            onChunk.accept(capabilityAnswer());
            return;
        }
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
                .header(HttpHeaders.AUTHORIZATION, bearer(aiProperties.nextApiKey()))
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
        String prompt = "HÃ£y Ä‘á»c vÃ  mÃ´ táº£ ná»™i dung áº£nh nÃ y báº±ng tiáº¿ng Viá»‡t. "
                + "Náº¿u áº£nh cÃ³ chá»¯, báº£ng biá»ƒu, giao diá»‡n, lá»—i, sá»‘ liá»‡u hoáº·c thÃ´ng tin quan trá»ng, hÃ£y trÃ­ch xuáº¥t rÃµ. "
                + "TÃªn file: " + (fileName == null ? "image" : fileName);

        Map<String, Object> payload = Map.of(
                "model", aiProperties.getVisionModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Báº¡n lÃ  trá»£ lÃ½ Ä‘á»c hÃ¬nh áº£nh vÃ  trÃ­ch xuáº¥t thÃ´ng tin Ä‘á»ƒ dÃ¹ng cho RAG."),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))),
                "temperature", aiProperties.getTemperature());

        Map<?, ?> response = postJsonWithKeyRotation("/chat/completions", payload);

        String text = extractMessageContent(response);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Unable to parse image description from OpenRouter response");
        }
        return text.trim();
    }

    private Map<?, ?> postJsonWithKeyRotation(String uri, Map<String, Object> payload) {
        int attempts = Math.max(1, aiProperties.configuredApiKeys().size());
        RestClientResponseException lastRetryable = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return openRouterRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aiProperties.nextApiKey()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientResponseException ex) {
                if (!isRetryableOpenRouterKeyError(ex) || attempts == 1) {
                    throw ex;
                }
                lastRetryable = ex;
                log.warn("OpenRouter key failed with status {}; trying another configured key", ex.getStatusCode().value());
            }
        }
        throw lastRetryable;
    }

    private boolean isRetryableOpenRouterKeyError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 401 || status == 402 || status == 429;
    }

    private String bearer(String apiKey) {
        return "Bearer " + (apiKey == null ? "" : apiKey);
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
                                "Báº¡n chuyá»ƒn cÃ¢u tráº£ lá»i sang tiáº¿ng Viá»‡t tá»± nhiÃªn, dÃ¹ng Markdown, giá»¯ Ä‘Ãºng ná»™i dung vÃ  citation náº¿u cÃ³."),
                        Map.of("role", "user", "content",
                                "Ngá»¯ cáº£nh yÃªu cáº§u ban Ä‘áº§u:\n" + originalPrompt
                                        + "\n\nCÃ¢u tráº£ lá»i cáº§n sá»­a:\n" + answer
                                        + "\n\nHÃ£y viáº¿t láº¡i báº±ng tiáº¿ng Viá»‡t, Markdown dá»… Ä‘á»c, khÃ´ng thÃªm thÃ´ng tin khÃ´ng cÃ³.")),
                "temperature", 0.1);

        Map<?, ?> response = postJsonWithKeyRotation("/chat/completions", payload);

        String repaired = extractMessageContent(response);
        return repaired == null || repaired.isBlank() ? answer : normalizeMarkdown(repaired.trim());
    }

    private boolean isSmallTalkOrCapabilityQuestion(String question, String documentContext) {
        if (documentContext != null && !documentContext.isBlank()) {
            return false;
        }
        if (question == null) {
            return false;
        }
        String normalized = normalizeForIntent(question);
        boolean greeting = normalized.matches("^(hi|hello|hey|xin chao|chao|alo|yo)(\\b|[!.?,\\s]).*")
                || normalized.matches(".*\\b(chao ban|xin chao ban)\\b.*");
        boolean asksCapability = normalized.contains("ban co the giup gi")
                || normalized.contains("co the giup gi")
                || normalized.contains("ban lam duoc gi")
                || normalized.contains("ban giup duoc gi")
                || normalized.contains("giup gi cho minh")
                || normalized.contains("help me with")
                || normalized.contains("what can you do");
        boolean explicitProjectRequest = normalized.contains("issue")
                || normalized.contains("task")
                || normalized.contains("sprint")
                || normalized.contains("rui ro")
                || normalized.contains("workload")
                || normalized.contains("bao cao")
                || normalized.contains("lap ke hoach")
                || normalized.contains("cap nhat")
                || normalized.contains("chuyen trang thai");
        return (greeting || asksCapability) && !explicitProjectRequest;
    }

    private String capabilityAnswer() {
        return "Xin chào! Mình có thể giúp bạn hỏi đáp, tóm tắt tình hình project, lập top việc ưu tiên, phân tích rủi ro/workload và chuẩn bị thao tác cập nhật issue qua nút Allow khi cần.";
    }

    private String normalizeForIntent(String text) {
        String lowered = text.toLowerCase().trim();
        String decomposed = java.text.Normalizer.normalize(lowered, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
    }

    private String normalizeMarkdown(String text) {
        return AgentMarkdownNormalizer.normalize(text);
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
                " vÃ  ", " lÃ  ", " cá»§a ", " trong ", " ngÆ°á»i ", " tÃ i liá»‡u ", " dá»± Ã¡n ", " cáº§n ", " khÃ´ng ",
                " file ", " giáº£i thÃ­ch ");
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
                .append("\n- Báº¯t buá»™c tráº£ lá»i báº±ng tiáº¿ng Viá»‡t tá»± nhiÃªn, cÃ³ dáº¥u.")
                .append("\n- Tráº£ lá»i Ä‘Ãºng cÃ¢u há»i, khÃ´ng Ã©p má»™t template cá»‘ Ä‘á»‹nh cho má»i tÃ¬nh huá»‘ng.")
                .append("\n- CÃ¢u há»i Ä‘Æ¡n giáº£n thÃ¬ tráº£ lá»i ngáº¯n; cÃ¢u há»i phÃ¢n tÃ­ch hoáº·c yÃªu cáº§u chi tiáº¿t thÃ¬ dÃ¹ng Markdown rÃµ rÃ ng.")
                .append("\n- Náº¿u ngÆ°á»i dÃ¹ng yÃªu cáº§u má»Ÿ rá»™ng nhÆ° 'dÃ i ra', 'cá»¥ thá»ƒ hÆ¡n', 'giáº£i thÃ­ch thÃªm', hÃ£y má»Ÿ rá»™ng dá»±a trÃªn cÃ¢u tráº£ lá»i trÆ°á»›c trong Recent conversation memory.")
                .append("\n- Náº¿u cÃ³ Document context, hÃ£y Æ°u tiÃªn ná»™i dung tÃ i liá»‡u vÃ  khÃ´ng tráº£ lá»i chung chung.")
                .append("\n- Náº¿u tÃ i liá»‡u cÃ³ Ã­t thÃ´ng tin, nÃ³i rÃµ pháº§n nÃ o láº¥y tá»« tÃ i liá»‡u vÃ  pháº§n nÃ o lÃ  suy luáº­n.")
                .append("\n- Khi dÃ¹ng ná»™i dung tÃ i liá»‡u, cite marker Ä‘Ãºng dáº¡ng [fileName #chunkIndex] náº¿u context cÃ³ marker.")
                .append("\n- Náº¿u cÃ¢u há»i mÆ¡ há»“ nhÆ°ng Ä‘Ã£ cÃ³ tÃ i liá»‡u/memory, tá»± chá»n cÃ¡ch giáº£i thÃ­ch há»¯u Ã­ch nháº¥t thay vÃ¬ há»i láº¡i ngay.")
                .append("\n- Náº¿u chá»‰ lÃ  cÃ¢u chÃ o/cÃ¢u xÃ£ giao, tráº£ lá»i ngáº¯n trong 1-2 cÃ¢u.");

        if (documentContext != null && !documentContext.isBlank()) {
            prompt.append("\n\nDocument answer guidance:")
                    .append("\n- DÃ¹ng heading ngáº¯n khi cáº§n, khÃ´ng báº¯t buá»™c Ä‘á»§ bá»‘n má»¥c náº¿u cÃ¢u há»i khÃ´ng cáº§n.")
                    .append("\n- Vá»›i cÃ¢u há»i 'file nÃ y cÃ³ gÃ¬', nÃªn gá»“m: tá»•ng quan, Ã½ chÃ­nh, Ä‘iá»ƒm quan trá»ng, vÃ­ dá»¥ náº¿u phÃ¹ há»£p.")
                    .append("\n- Code hoáº·c lá»‡nh nÃªn Ä‘áº·t trong fenced code block.");
        }

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
        if (!aiProperties.hasApiKey()) {
            throw new IllegalStateException("OPENROUTER_API_KEY or OPENROUTER_API_KEYS is not configured");
        }
    }
}
