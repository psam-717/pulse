package com.example.demo.dto;

import java.util.List;

/**
 * Patient medical records — GET /api/patients/me/records.
 * Shapes match pulse-mobile stores/records-store.ts exactly.
 */
public record MedicalRecordsResponse(
        List<Visit> visits,
        List<LabResult> labResults,
        List<Prescription> prescriptions
) {
    public record Visit(
            String id,
            String department,
            String hospital,
            String date,
            String doctor,
            String summary
    ) {}

    public record LabValue(
            String name,
            String value,
            String unit,
            String referenceRange
    ) {}

    public record LabResult(
            String id,
            String testName,
            String hospital,
            String orderingDoctor,
            String date,
            List<LabValue> values
    ) {}

    public record Prescription(
            String id,
            String medication,
            String dose,
            String prescribingDoctor,
            String hospital,
            String date
    ) {}
}
