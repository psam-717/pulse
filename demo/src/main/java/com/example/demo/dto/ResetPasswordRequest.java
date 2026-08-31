package com.example.demo.dto;

/**
 * Step 2 of the patient forgot-password flow (BE-11).
 * Carries the same identifier as step 1, the OTP the patient received,
 * and the new password.
 */
public record ResetPasswordRequest(
        String identifier,
        String otp,
        String newPassword
) {}
