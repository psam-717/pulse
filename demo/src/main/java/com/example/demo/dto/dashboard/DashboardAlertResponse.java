package com.example.demo.dto.dashboard;

/** GET /api/dashboard/alerts row — BACKEND_SPEC §5.9 (DashboardAlert). */
public record DashboardAlertResponse(
        String id,
        String severity,   // "critical" | "warning" | "info"
        String title,
        String description
) {}
