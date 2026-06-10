package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record LicenseVerifyRequest(

        @NotNull(message = "Verification status is required")
        String status,       // "APPROVED" or "REJECTED"

        String rejectionReason
) {}