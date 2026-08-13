package com.example.demo.dto;

/** Web dashboard AppointmentStats shape (BACKEND_SPEC.md §5.2). */
public record AppointmentStatsResponse(
        int total,
        int scheduled,
        int confirmed,
        int checkedIn,
        int completed,
        int cancelled,
        int noShow
) {}
