package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for POST /api/queue/entries/{id}/complete — a clinician closes an
 * in-consultation ticket: the entry moves to COMPLETED, a linked booking
 * flips to 'completed' (the queue is the authority), and the consult is
 * snapshotted into the patient's medical record (one visit note plus one
 * prescription per item). All fields optional; an empty consult still
 * closes the ticket.
 */
public record CompleteConsultRequest(
        @Size(max = 5000, message = "Summary too long")
        String summary,
        @Size(max = 2000, message = "Symptoms too long")
        String symptoms,
        @Size(max = 2000, message = "Recommendations too long")
        String recommendations,
        List<@Valid PrescriptionItem> prescriptions
) {
    /** One prescribed medication to snapshot into the patient's records. */
    public record PrescriptionItem(
            @NotBlank(message = "Medication is required")
            @Size(max = 200, message = "Medication name too long")
            String medication,
            @NotBlank(message = "Dose is required")
            @Size(max = 120, message = "Dose too long")
            String dose,
            @Size(max = 500, message = "Instructions too long")
            String instructions
    ) {}
}
