package com.iems.aiservice.service;

import com.iems.aiservice.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenRouterEmbeddingService {

    private final RestClient openRouterRestClient;
    private final AiProperties aiProperties;

    public List<Double> embed(String text) {
        ensureApiKeyConfigured();

        Map<String, Object> payload = Map.of(
                "model", aiProperties.getEmbeddingModel(),
                "input", text,
                "encoding_format", "float");

        Map<?, ?> response = openRouterRestClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response != null && response.get("data") instanceof List<?> data && !data.isEmpty()) {
            Object first = data.get(0);
            if (first instanceof Map<?, ?> item && item.get("embedding") instanceof List<?> vector) {
                return vector.stream().map(v -> ((Number) v).doubleValue()).toList();
            }
        }

        throw new IllegalStateException("Unable to parse embedding from OpenRouter response");
    }

    private void ensureApiKeyConfigured() {
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
        }
    }
}
