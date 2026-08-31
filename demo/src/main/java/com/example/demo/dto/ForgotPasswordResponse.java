package com.example.demo.dto;

/**
 * Response for POST /api/auth/patient/forgot-password (BE-11).
 *
 * <p>{@code message} is ALWAYS the same, whether or not an account exists —
 * revealing the difference would let attackers enumerate valid identifiers
 * (bug-triage BE-1 lesson). {@code devOtp} is echoed only in dev mode
 * ({@code otp.dev-mode=true}) and only when a code was actually issued;
 * production never returns it.
 */
public record ForgotPasswordResponse(
        String message,
        String devOtp
) {}
