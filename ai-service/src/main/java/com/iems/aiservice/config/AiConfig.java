package com.iems.aiservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public RestClient openRouterRestClient(AiProperties aiProperties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl());

        if (aiProperties.getHttpReferer() != null && !aiProperties.getHttpReferer().isBlank()) {
            builder.defaultHeader("HTTP-Referer", aiProperties.getHttpReferer());
        }
        if (aiProperties.getTitle() != null && !aiProperties.getTitle().isBlank()) {
            builder.defaultHeader("X-Title", aiProperties.getTitle());
        }

        return builder.build();
    }
}
