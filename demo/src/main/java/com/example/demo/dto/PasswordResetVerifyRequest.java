package com.example.demo.dto;

/**
 * Step 1 of the FE password-reset contract (#33) — POST /api/auth/patient/password-reset/request.
 * Same semantics as ForgotPasswordRequest (identifier is phone | Ghana Card | patient number).
 */
public record PasswordResetVerifyRequest(
        String identifier,
        String code
) {}
