package com.example.demo.dto.analytics;

import java.util.List;

/** Per-department analytics — §5.10 (DepartmentAnalytics). */
public record DepartmentAnalyticsResponse(
        String departmentId,
        String departmentName,
        List<DailyMetricResponse> daily,
        AnalyticsTotalsResponse totals,
        AnalyticsTotalsResponse previousTotals,
        int capacityPerDay,    // v1 convention: department.rooms x 10 (documented assumption)
        int utilization        // 0-100, served vs capacity over the range
) {}
