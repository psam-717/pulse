package com.example.demo.dto;

/** Step 2 response: single-use capability token that authorizes the new password. */
public record StaffPasswordResetVerifyResponse(String resetToken) {}
