package com.iems.aiservice.service;

import com.iems.aiservice.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

        Map<?, ?> response = postJsonWithKeyRotation(payload);

        if (response != null && response.get("data") instanceof List<?> data && !data.isEmpty()) {
            Object first = data.get(0);
            if (first instanceof Map<?, ?> item && item.get("embedding") instanceof List<?> vector) {
                return vector.stream().map(v -> ((Number) v).doubleValue()).toList();
            }
        }

        throw new IllegalStateException("Unable to parse embedding from OpenRouter response");
    }

    private void ensureApiKeyConfigured() {
        if (!aiProperties.hasApiKey()) {
            throw new IllegalStateException("OPENROUTER_API_KEY or OPENROUTER_API_KEYS is not configured");
        }
    }

    private Map<?, ?> postJsonWithKeyRotation(Map<String, Object> payload) {
        int attempts = Math.max(1, aiProperties.configuredApiKeys().size());
        RestClientResponseException lastRetryable = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return openRouterRestClient.post()
                        .uri("/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.nextApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientResponseException ex) {
                if (!isRetryableOpenRouterKeyError(ex) || attempts == 1) {
                    throw ex;
                }
                lastRetryable = ex;
            }
        }
        throw lastRetryable;
    }

    private boolean isRetryableOpenRouterKeyError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 401 || status == 402 || status == 429;
    }
}
