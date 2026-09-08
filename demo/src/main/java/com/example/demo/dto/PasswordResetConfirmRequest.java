package com.example.demo.dto;

/**
 * Step 3 — POST /api/auth/patient/password-reset/confirm (FE contract #33).
 */
public record PasswordResetConfirmRequest(
        String identifier,
        String resetToken,
        String newPassword
) {}
