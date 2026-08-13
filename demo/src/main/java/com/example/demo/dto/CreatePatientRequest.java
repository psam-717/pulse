package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** CreatePatientInput — POST /api/patients body (BACKEND_SPEC.md §5.5). */
public record CreatePatientRequest(
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "dateOfBirth is required (YYYY-MM-DD)")
        String dateOfBirth,
        @NotBlank(message = "gender is required")
        @Pattern(regexp = "female|male|other", message = "gender must be one of: female, male, other")
        String gender,
        @NotBlank(message = "phone is required")
        String phone,
        @Email(message = "Invalid email format")
        String email,
        String address,
        String bloodType
) {}
