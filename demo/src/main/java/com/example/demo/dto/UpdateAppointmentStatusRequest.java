package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** UpdateAppointmentInput — PATCH /appointments/{id} body (BACKEND_SPEC.md §6.1). */
public record UpdateAppointmentStatusRequest(
        @NotBlank(message = "status is required")
        String status
) {}
