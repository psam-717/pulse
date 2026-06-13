package com.example.demo.config;

import com.example.demo.dto.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public health check
                .requestMatchers("/api/hello", "/api/status").permitAll()
                // Swagger UI / OpenAPI docs (public)
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/scalar/**").permitAll()
                // Public auth endpoints
                .requestMatchers("/api/auth/patient/signup", "/api/auth/patient/verify-otp").permitAll()
                .requestMatchers("/api/auth/patient/login", "/api/auth/doctor/login").permitAll()
                .requestMatchers("/api/auth/admin/**").permitAll()
                // Hospital registration & login -- public
                .requestMatchers("/api/hospitals/register", "/api/hospitals/login").permitAll()
                // Discovery endpoints -- public (GET only)
                .requestMatchers(HttpMethod.GET, "/api/hospitals/**", "/api/doctors/**", "/api/departments/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String msg;
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || authHeader.isBlank()) {
                msg = "Authentication required. Provide a valid Bearer token in the Authorization header.";
            } else if (!authHeader.startsWith("Bearer ")) {
                msg = "Invalid Authorization header format. The Authorization header must start with Bearer.";
            } else {
                msg = "Invalid or expired token. Please log in again.";
            }

            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.error(401, msg)
            );
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.error(403, "Access denied. You don't have permission to perform this action.")
            );
        };
    }
}