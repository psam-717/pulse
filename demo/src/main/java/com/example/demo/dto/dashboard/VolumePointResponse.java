package com.example.demo.dto.dashboard;

/** GET /api/dashboard/patient-volume row — BACKEND_SPEC §5.9 (VolumePoint). */
public record VolumePointResponse(
        String hour,        // "8 AM" … "6 PM"
        int walkIns,
        int appointments
) {}
