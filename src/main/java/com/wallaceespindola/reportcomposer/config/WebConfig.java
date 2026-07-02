package com.wallaceespindola.reportcomposer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // POC: frontend may be served from another origin (nginx on :3000)
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET", "POST");
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Report Composer POC API")
                        .description("Distributed report generation — Spring Batch remote partitioning over Kafka")
                        .version("v1")
                        .contact(new Contact()
                                .name("Wallace Espindola")
                                .email("wallace.espindola@gmail.com")
                                .url("https://github.com/wallaceespindola/")));
    }
}
