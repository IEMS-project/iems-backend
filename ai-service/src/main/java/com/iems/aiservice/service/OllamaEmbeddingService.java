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
public class OllamaEmbeddingService {

    private final RestClient ollamaRestClient;
    private final AiProperties aiProperties;

    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        try {
            Map<String, Object> payload = Map.of(
                    "model", aiProperties.getEmbeddingModel(),
                    "input", text);
            Map<String, Object> response = ollamaRestClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("embeddings") instanceof List<?> embeddings && !embeddings.isEmpty()) {
                Object first = embeddings.get(0);
                if (first instanceof List<?> vector) {
                    return vector.stream().map(v -> ((Number) v).doubleValue()).toList();
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        Map<String, Object> payload = Map.of(
                "model", aiProperties.getEmbeddingModel(),
                "prompt", text);
        Map<String, Object> response = ollamaRestClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("embedding") instanceof List<?> vector)) {
            throw new IllegalStateException("Unable to get embeddings from Ollama");
        }

        return vector.stream().map(v -> ((Number) v).doubleValue()).toList();
    }
}
