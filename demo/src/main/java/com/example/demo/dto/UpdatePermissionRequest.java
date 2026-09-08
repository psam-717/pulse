package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** PATCH /settings/permissions. level ∈ none|view|edit (validated in service). */
public record UpdatePermissionRequest(
        @NotBlank String resource,
        @NotBlank String role,
        @NotBlank String level
) {
}
