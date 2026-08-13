package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/** UpdatePatientInput — PATCH /api/patients/{id} body; all fields optional. */
public record UpdatePatientRequest(
        String name,
        String dateOfBirth,
        @Pattern(regexp = "female|male|other", message = "gender must be one of: female, male, other")
        String gender,
        String phone,
        @Email(message = "Invalid email format")
        String email,
        String address,
        String bloodType
) {}
