package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        Long patientId,

        @NotNull(message = "timeSlotId is required")
        Long timeSlotId
) {}
