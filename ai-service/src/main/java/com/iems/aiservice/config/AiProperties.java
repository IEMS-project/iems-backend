package com.iems.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.openrouter")
public class AiProperties {

    private String baseUrl;
    private String apiKey;
    private String httpReferer;
    private String title;
    private String model;
    private String visionModel;
    private String embeddingModel = "nvidia/llama-nemotron-embed-vl-1b-v2:free";
    private String systemPromptFile = "classpath:prompts/systemt_prompt.txt";
    private double temperature = 0.2;
    private int retrievalTopK = 6;
    private int chunkSize = 900;
    private int chunkOverlap = 150;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHttpReferer() {
        return httpReferer;
    }

    public void setHttpReferer(String httpReferer) {
        this.httpReferer = httpReferer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVisionModel() {
        return visionModel == null || visionModel.isBlank() ? model : visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getSystemPromptFile() {
        return systemPromptFile;
    }

    public void setSystemPromptFile(String systemPromptFile) {
        this.systemPromptFile = systemPromptFile;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
