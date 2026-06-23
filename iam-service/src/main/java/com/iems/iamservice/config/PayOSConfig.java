package com.iems.iamservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import vn.payos.PayOS;

@Configuration
public class PayOSConfig {

    @Bean
    public PayOS payOS(
            @Value("${payos.client-id:}") String clientId,
            @Value("${payos.api-key:}") String apiKey,
            @Value("${payos.checksum-key:}") String checksumKey
    ) {
        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(checksumKey)) {
            throw new IllegalStateException("Missing payOS credentials. Set PAYOS_CLIENT_ID, PAYOS_API_KEY, and PAYOS_CHECKSUM_KEY.");
        }

        return new PayOS(clientId, apiKey, checksumKey);
    }
}
