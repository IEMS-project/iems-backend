package com.iems.projectservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
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
                // Forward Authorization header from current request to Feign request
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
                    // Ignore exceptions when trying to forward headers
                }
            }
        };
    }

    @Bean
    public Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        return new SpringFormEncoder(new SpringEncoder(messageConverters));
    }
}
