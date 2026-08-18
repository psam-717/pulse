package com.example.demo.dto;

import java.util.List;

/** UpdateClinicalRecordInput — PATCH /api/patients/{id}/clinical-record body. */
public record UpdateClinicalRecordRequest(
        List<String> allergies,
        List<Medication> currentMedications
) {
    public record Medication(String name, String dose, String frequency) {}
}
