package com.reqwise.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reqWiseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReqWise API")
                        .description("Automated SRS Analysis & Requirements Intelligence Platform API")
                        .version("0.1.0")
                        .contact(new Contact().name("ReqWise Engineering")));
    }
}
