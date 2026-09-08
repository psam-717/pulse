package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/queue/entries — walk-in registration (BACKEND_SPEC §10.1).
 * Registers an EXISTING patient into a facility department's waiting queue
 * without an appointment. priority ∈ routine|urgent|emergency (lowercase,
 * optional → routine).
 */
public record CreateWalkInRequest(
        @NotNull(message = "patientId is required") Long patientId,
        @NotBlank(message = "departmentId is required") String departmentId,
        String priority
) {
}
