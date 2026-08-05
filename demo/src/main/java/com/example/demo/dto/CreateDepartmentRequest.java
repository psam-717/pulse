package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CreateDepartmentInput (BACKEND_SPEC.md §5.3). totalDoctors is accepted
 * for contract parity but the backend derives staffing counts from the
 * Staff domain — it is not stored.
 */
public record CreateDepartmentRequest(
        @NotBlank(message = "Department name is required")
        String name,
        @NotBlank(message = "Department code is required")
        String code,
        String description,
        String headDoctorName,
        Integer totalDoctors,
        @NotNull(message = "Number of rooms is required")
        Integer rooms,
        @NotBlank(message = "opensAt is required")
        String opensAt,
        @NotBlank(message = "closesAt is required")
        String closesAt,
        Boolean twentyFourSeven
) {}
