package com.example.demo.dto;

import java.util.List;

/**
 * Update own medical profile — PATCH /api/patients/me/medical.
 * All fields optional. Lists REPLACE the stored lists entirely
 * (the mobile store sends the full edited array, server assigns ids).
 */
public record UpdateMedicalProfileRequest(
        String bloodGroup,
        List<AllergyInput> allergies,
        List<ConditionInput> conditions,
        List<MedicationInput> medications
) {
    public record AllergyInput(String label, String type) {}
    public record ConditionInput(String label) {}
    public record MedicationInput(String name, String dose) {}
}
