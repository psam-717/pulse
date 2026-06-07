package com.example.demo.dto;

public record AuthResponse(
        String token,
        String role,
        Long userId,
        String message
) {}