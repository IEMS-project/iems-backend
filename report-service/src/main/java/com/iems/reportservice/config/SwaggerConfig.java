package com.iems.reportservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI reportServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("IEMS Report & File Service API")
                        .description("API documentation for Report Service and File Service")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("IEMS Team")
                                .email("support@iems.com")
                                .url("https://iems.com"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .externalDocs(new ExternalDocumentation()
                        .description("IEMS Project Documentation")
                        .url("https://iems.com/docs"));
    }
}
