package com.example.demo.dto;

/**
 * Facility-plane login response. In the 2FA flow the first call
 * (POST /api/auth/login) returns {@code token = null} plus the session and a
 * message that a verification code was sent; the real JWT comes from
 * POST /api/auth/login/verify-otp. {@code devOtp} echoes the code only in
 * dev mode (otp.dev-mode=true) — never in production.
 */
public record LoginResponse(
        String token,
        String role,
        Long userId,
        String message,
        WorkspaceSessionResponse session,
        String devOtp
) {}
