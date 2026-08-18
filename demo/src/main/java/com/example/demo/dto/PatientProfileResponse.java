package com.example.demo.dto;

/**
 * Patient self-service profile — GET /api/patients/me.
 * Shape mirrors ARCHITECTURE.md §8 P1 / mobile onboarding (step1-identity).
 */
public record PatientProfileResponse(
        String id,              // patientNumber, e.g. "PT-00101"
        String firstName,
        String lastName,
        String dateOfBirth,     // ISO date, e.g. 1998-04-12
        String gender,          // "male" | "female" | "other"
        String email,
        String phone,
        String ghanaCard,
        String address,
        EmergencyContact emergencyContact
) {
    public record EmergencyContact(String name, String relationship, String phone) {}
}
