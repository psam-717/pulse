package com.example.demo.dto;

/**
 * Step 1 of the patient forgot-password flow (BE-11).
 * {@code identifier} is the same login identifier the patient uses —
 * phone number, Ghana Card number, or patient number.
 */
public record ForgotPasswordRequest(
        String identifier
) {}
