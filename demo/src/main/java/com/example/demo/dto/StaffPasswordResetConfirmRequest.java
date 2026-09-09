package com.example.demo.dto;

/** Step 3 of the facility staff password reset: token + new password. */
public record StaffPasswordResetConfirmRequest(String email, String resetToken, String newPassword) {}
