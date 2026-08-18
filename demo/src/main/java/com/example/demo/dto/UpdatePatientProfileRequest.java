package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

/**
 * Update own profile — PATCH /api/patients/me. All fields optional;
 * only provided fields are applied. Mirrors mobile onboarding step1.
 */
public record UpdatePatientProfileRequest(
        String firstName,
        String lastName,
        String dateOfBirth,     // ISO date yyyy-MM-dd
        @Pattern(regexp = "male|female|other", message = "gender must be one of: male, female, other")
        String gender,
        @Email(message = "Invalid email format")
        String email,
        String phone,
        String address,
        EmergencyContact emergencyContact
) {
    public record EmergencyContact(String name, String relationship, String phone) {}
}
