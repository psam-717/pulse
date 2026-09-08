package com.example.demo.dto.dashboard;

/** GET /api/dashboard/queue row — BACKEND_SPEC §5.9 (DepartmentQueue). */
public record DashboardDepartmentQueueResponse(
        String id,
        String department,
        String statusLabel,  // e.g. "Serving #C-001"
        int waiting,
        int maxWaitMinutes,
        String severity      // "ok" | "warning" | "critical"
) {}
