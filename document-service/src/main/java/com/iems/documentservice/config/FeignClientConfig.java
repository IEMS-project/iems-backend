package com.iems.documentservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                try {
                    var requestAttributes = RequestContextHolder.getRequestAttributes();
                    if (requestAttributes instanceof ServletRequestAttributes servletAttrs) {
                        HttpServletRequest request = servletAttrs.getRequest();
                        String authHeader = request.getHeader("Authorization");
                        if (authHeader != null && !authHeader.isBlank()) {
                            template.header("Authorization", authHeader);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        };
    }

    @Bean
    public Retryer feignRetryer() {
        // Retry with 100ms initial interval, 1000ms max interval, and 3 max attempts.
        return new Retryer.Default(100, 1000, 3);
    }
}
