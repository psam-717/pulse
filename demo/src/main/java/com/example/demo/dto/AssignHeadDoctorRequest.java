package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** AssignHeadDoctorInput (BACKEND_SPEC.md §5.3). */
public record AssignHeadDoctorRequest(
        @NotBlank(message = "headDoctorName is required")
        String headDoctorName
) {}
