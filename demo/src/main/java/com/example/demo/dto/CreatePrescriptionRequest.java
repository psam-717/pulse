package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Staff authoring of a prescription — POST /api/patients/{id}/records/prescriptions. */
public record CreatePrescriptionRequest(
        @NotBlank(message = "Medication is required")
        @Size(max = 200, message = "Medication name too long")
        String medication,
        @NotBlank(message = "Dose is required")
        @Size(max = 120, message = "Dose too long")
        String dose,
        // Optional YYYY-MM-DD; defaults to today.
        String prescribedDate
) {}
