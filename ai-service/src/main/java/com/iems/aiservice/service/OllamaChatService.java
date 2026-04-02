package com.iems.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iems.aiservice.config.AiProperties;
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
public class OllamaChatService {

    private final RestClient ollamaRestClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public OllamaChatService(RestClient ollamaRestClient, AiProperties aiProperties, ObjectMapper objectMapper) {
        this.ollamaRestClient = ollamaRestClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public String ask(String question) {
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", aiProperties.getSystemPrompt()),
                        Map.of("role", "user", "content", question)),
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
                return text.trim();
            }
        }

        throw new IllegalStateException("Unable to parse answer from Ollama response");
    }

    public void streamAsk(String question, Consumer<String> onChunk) {
        Map<String, Object> payload = Map.of(
                "model", aiProperties.getModel(),
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", aiProperties.getSystemPrompt()),
                        Map.of("role", "user", "content", question)),
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
                                break;
                            }
                        }
                    }
                    return null;
                });
    }
}
