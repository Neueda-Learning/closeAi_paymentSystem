package com.hsbc.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI paymentSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Processing System API")
                        .version("1.0.0")
                        .description("REST API for managing the complete lifecycle of financial payments"));
    }
}
