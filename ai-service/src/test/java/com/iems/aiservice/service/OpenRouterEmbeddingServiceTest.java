package com.iems.aiservice.service;

import com.iems.aiservice.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OpenRouterEmbeddingServiceTest {

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestSpec;
    private RestClient.ResponseSpec responseSpec;
    private AiProperties properties;
    private OpenRouterEmbeddingService service;

    @BeforeEach
    void setUp() {
        restClient = Mockito.mock(RestClient.class);
        requestSpec = Mockito.mock(RestClient.RequestBodyUriSpec.class, Mockito.RETURNS_SELF);
        responseSpec = Mockito.mock(RestClient.ResponseSpec.class);
        properties = new AiProperties();
        properties.setEmbeddingModel("embed-model");
        properties.setApiKey("test-key");
        service = new OpenRouterEmbeddingService(restClient, properties);
    }

    @Test
    void embedShouldParseEmbeddingVector() {
        Map<String, Object> response = Map.of(
                "data", List.of(Map.of("embedding", List.of(1, 2.5, 3.0)))
        );
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri("/embeddings")).thenReturn(requestSpec);
        when(requestSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestSpec);
        when(requestSpec.body(any())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(response);

        List<Double> vector = service.embed("hello");

        assertEquals(List.of(1.0, 2.5, 3.0), vector);
    }

    @Test
    void embedShouldRejectMissingApiKey() {
        properties.setApiKey("   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.embed("hello"));
        assertEquals("OPENROUTER_API_KEY is not configured", ex.getMessage());
    }

    @Test
    void embedShouldFailOnInvalidResponse() {
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri("/embeddings")).thenReturn(requestSpec);
        when(requestSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestSpec);
        when(requestSpec.body(any())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(Map.of("data", List.of(Map.of("wrong", 1))));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.embed("hello"));
        assertEquals("Unable to parse embedding from OpenRouter response", ex.getMessage());
    }
}