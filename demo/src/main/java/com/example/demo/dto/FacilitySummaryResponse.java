package com.example.demo.dto;

/** GET /api/facility/current — BACKEND_SPEC §5.9 (FacilitySummary). */
public record FacilitySummaryResponse(
        String id,
        String name
) {}
