package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public health check
                .requestMatchers("/api/hello", "/api/status").permitAll()
                // Public auth endpoints (bootstrap + login)
                .requestMatchers("/api/auth/patient/signup", "/api/auth/patient/verify-otp").permitAll()
                .requestMatchers("/api/auth/patient/login", "/api/auth/doctor/login").permitAll()
                .requestMatchers("/api/auth/admin/**").permitAll()
                // Discovery endpoints — permit during development
                .requestMatchers(HttpMethod.GET, "/api/hospitals/**", "/api/doctors/**", "/api/departments/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}