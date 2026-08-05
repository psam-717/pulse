package com.example.demo.dto;

/**
 * UpdateDepartmentInput body (BACKEND_SPEC.md §5.3) — all fields optional
 * shallow patch. The service only applies non-null fields and validates
 * {@code status} against active | closed | archived.
 */
public record UpdateDepartmentRequest(
        String name,
        String code,
        String description,
        String headDoctorName,
        Integer totalDoctors,
        Integer rooms,
        String opensAt,
        String closesAt,
        Boolean twentyFourSeven,
        String status
) {}
