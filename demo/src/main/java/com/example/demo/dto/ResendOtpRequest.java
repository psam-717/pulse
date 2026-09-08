package com.example.demo.dto;

/** POST /api/auth/patient/resend-otp — re-issues the signup OTP for a pending registration. */
public record ResendOtpRequest(
        String phone
) {}
