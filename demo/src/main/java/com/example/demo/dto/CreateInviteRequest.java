package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /settings/invites. role = StaffRole lowercase (admin|doctor|nurse|front_desk|read_only). */
public record CreateInviteRequest(
        @NotBlank @Email String email,
        @NotBlank String role
) {
}
