package com.example.demo.dto.settings;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/settings/profile/change-password body (BACKEND_SPEC.md §5.8).
 * currentPassword is verified against the stored BCrypt hash; newPassword
 * must be at least 8 characters (enforced server-side).
 */
public record ChangePasswordInput(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,
        @NotBlank(message = "newPassword is required")
        String newPassword
) {}
