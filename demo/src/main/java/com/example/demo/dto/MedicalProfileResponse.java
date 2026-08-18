package com.example.demo.dto;

import java.util.List;

/**
 * Patient medical profile — GET /api/patients/me/medical.
 * Shapes mirror the mobile medical-store exactly (ARCHITECTURE.md §5.2):
 * AllergyEntry {id,label,type}, ConditionEntry {id,label},
 * MedicationEntry {id,name,dose}, VitalsEntry {id,date,systolic,...}.
 */
public record MedicalProfileResponse(
        String bloodGroup,
        List<Allergy> allergies,
        List<Condition> conditions,
        List<Medication> medications,
        List<VitalsEntry> vitals
) {
    public record Allergy(String id, String label, String type) {}
    public record Condition(String id, String label) {}
    public record Medication(String id, String name, String dose) {}
    public record VitalsEntry(String id, String date, String systolic, String diastolic,
                              String pulseBpm, String temperatureC,
                              String heightCm, String weightKg) {}
}
