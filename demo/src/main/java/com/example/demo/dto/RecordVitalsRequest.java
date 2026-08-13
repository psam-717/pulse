package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** RecordVitalsInput — POST /api/patients/{id}/vitals body (recordedAt server-set). */
public record RecordVitalsRequest(
        @NotBlank(message = "bloodPressure is required (e.g. 120/80)")
        String bloodPressure,
        @NotBlank(message = "temperature is required (e.g. 37.0°C)")
        String temperature,
        @NotBlank(message = "pulse is required (e.g. 72 bpm)")
        String pulse,
        @NotBlank(message = "weight is required (e.g. 68 kg)")
        String weight
) {}
