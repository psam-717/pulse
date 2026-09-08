package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Staff authoring of a consultation note — POST /api/patients/{id}/records/visits. */
public record CreateVisitNoteRequest(
        @NotBlank(message = "Summary is required")
        @Size(max = 5000, message = "Summary too long")
        String summary,
        // Optional YYYY-MM-DD; defaults to today.
        String visitDate
) {}
