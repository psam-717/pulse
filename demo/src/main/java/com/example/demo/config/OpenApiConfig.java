package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pulse Healthcare API")
                        .description("A hospital booking and management platform connecting patients with healthcare facilities in Ghana. Manages hospital registration, doctor scheduling, appointment booking, and real-time queue tracking.")
                        .version("v2.0.0")
                        .contact(new Contact()
                                .name("Marvinphil Annorbah")
                                .email("mphilannorbah@gmail.com")
                                .url("https://github.com/psam-717/pulse"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}