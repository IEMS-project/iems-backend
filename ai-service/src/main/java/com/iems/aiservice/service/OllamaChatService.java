package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class OllamaChatService {

    private final RestClient ollamaRestClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public OllamaChatService(RestClient ollamaRestClient, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.ollamaRestClient = ollamaRestClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public String ask(String question, List<String> selectedDocumentIds, String documentContext) {
        String scopedQuestion = buildScopedQuestion(question, selectedDocumentIds, documentContext);
        log.info("Ollama ask start model={} selectedCount={} contextChars={} promptChars={}",
            aiProperties.getModel(),
            selectedDocumentIds == null ? 0 : selectedDocumentIds.size(),
            documentContext == null ? 0 : documentContext.length(),
            scopedQuestion.length());
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", aiProperties.getSystemPrompt()),
                        Map.of("role", "user", "content", scopedQuestion)),
                "options", Map.of("temperature", aiProperties.getTemperature()));

        Map<?, ?> response = ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Ollama returned empty response");
        }

        Object message = response.get("message");
        if (message instanceof Map<?, ?> msgMap) {
            Object content = msgMap.get("content");
            if (content instanceof String text && !text.isBlank()) {
                log.info("Ollama ask done answerChars={}", text.length());
                return text.trim();
            }
        }

        throw new IllegalStateException("Unable to parse answer from Ollama response");
    }

    public void streamAsk(String question,
                          List<String> selectedDocumentIds,
                          String documentContext,
                          Consumer<String> onChunk) {
        String scopedQuestion = buildScopedQuestion(question, selectedDocumentIds, documentContext);
        log.info("Ollama stream start model={} selectedCount={} contextChars={} promptChars={}",
            aiProperties.getModel(),
            selectedDocumentIds == null ? 0 : selectedDocumentIds.size(),
            documentContext == null ? 0 : documentContext.length(),
            scopedQuestion.length());
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", aiProperties.getSystemPrompt()),
                        Map.of("role", "user", "content", scopedQuestion)),
                "options", Map.of("temperature", aiProperties.getTemperature()));

        ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange((request, response) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) {
                                continue;
                            }

                            JsonNode node = objectMapper.readTree(line);
                            JsonNode contentNode = node.path("message").path("content");
                            if (!contentNode.isMissingNode() && !contentNode.asText().isBlank()) {
                                onChunk.accept(contentNode.asText());
                            }

                            if (node.path("done").asBoolean(false)) {
                                log.info("Ollama stream done");
                                break;
                            }
                        }
                    }
                    return null;
                });
    }

    private String buildScopedQuestion(String question, List<String> selectedDocumentIds, String documentContext) {
        if ((selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                && (documentContext == null || documentContext.isBlank())) {
            return question;
        }

        String scopeIds = (selectedDocumentIds == null || selectedDocumentIds.isEmpty())
                ? "none"
                : String.join(", ", selectedDocumentIds);

        StringBuilder prompt = new StringBuilder();
        prompt.append("User question: ").append(question)
                .append("\n\nSelected document scope IDs: ").append(scopeIds)
                .append("\nUse only the selected documents as context.");

        if (documentContext != null && !documentContext.isBlank()) {
            prompt.append("\n\nDocument context:\n").append(documentContext);
        }

        return prompt.toString();
    }
}
