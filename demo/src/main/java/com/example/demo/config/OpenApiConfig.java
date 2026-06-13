package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

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
                                .url("https://opensource.org/licenses/MIT")))
                // Bearer token auth — global; public endpoints opt-out via @Operation(security = {})
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token. Get one from POST /api/auth/login or /api/auth/register\n\n"
                                        + "Example: admin token → `Bearer eyJhbGciOiJIUzI1NiJ...`")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}