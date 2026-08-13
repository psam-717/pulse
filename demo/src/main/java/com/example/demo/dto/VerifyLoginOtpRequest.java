package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for POST /api/auth/login/verify-otp — email + 6-digit code. */
public record VerifyLoginOtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        @NotBlank(message = "Verification code is required")
        @Size(min = 6, max = 6, message = "Verification code must be exactly 6 digits")
        String code
) {}
