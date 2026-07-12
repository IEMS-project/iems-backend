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

    /**
     * Returns embed for open router embedding processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     *   <li>Coordinate with external services needed by the operation.</li>
     * </ul>
     *
     * @param text the text parameter
     * @return the matching result collection
     * @throws IllegalStateException if the requested operation cannot be completed
     */
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

    /**
     * Ensures that open router embedding requirements are satisfied.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Validate the request and enforce applicable business constraints.</li>
     * </ul>
     *
     * @throws IllegalStateException if the requested operation cannot be completed
     */
    private void ensureApiKeyConfigured() {
        if (!aiProperties.hasApiKey()) {
            throw new IllegalStateException("OPENROUTER_API_KEY or OPENROUTER_API_KEYS is not configured");
        }
    }

    /**
     * Returns post json with key rotation for open router embedding processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param payload the payload parameter
     * @return the post json with key rotation result
     */
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

    /**
     * Returns is retryable open router key error for open router embedding processing.
     *
     * <p><strong>Business:</strong></p>
     * <ul>
     *   <li>Transform domain data into the response required by the caller.</li>
     * </ul>
     *
     * @param ex the ex parameter
     * @return true if the requested condition is satisfied; otherwise false
     */
    private boolean isRetryableOpenRouterKeyError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 401 || status == 402 || status == 429;
    }
}
