package com.example.demo.dto;

/**
 * Self-logged vitals entry — POST /api/patients/me/vitals.
 * All fields optional (a patient may only note weight one day, BP the next —
 * matches mobile VitalsEntry semantics). Server assigns id + date.
 */
public record AddVitalsRequest(
        String systolic,
        String diastolic,
        String pulseBpm,
        String temperatureC,
        String heightCm,
        String weightKg
) {}
