package com.example.demo.dto;

/**
 * Facility-plane login response — extends the legacy AuthResponse shape
 * with the full {@code WorkspaceSession} the frontend's LoginResult expects.
 */
public record LoginResponse(
        String token,
        String role,
        Long userId,
        String message,
        WorkspaceSessionResponse session
) {}
