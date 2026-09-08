package com.example.demo.dto;

/**
 * Step 2 response — POST /api/auth/patient/password-reset/verify.
 * The resetToken is a high-entropy server-issued capability bound to the
 * patient's phone; the FE carries it to the confirm step (it never re-asks
 * for the 6-digit code, so the token cannot be the low-entropy code itself).
 */
public record PasswordResetVerifyResponse(
        String resetToken
) {}
