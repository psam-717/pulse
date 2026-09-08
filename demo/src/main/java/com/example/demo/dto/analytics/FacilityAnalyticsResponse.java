package com.example.demo.dto.analytics;

import java.util.List;

/** GET /api/analytics — §5.10 (FacilityAnalytics). One combined payload on purpose. */
public record FacilityAnalyticsResponse(
        DateRangeResponse range,
        List<DailyMetricResponse> daily,
        AnalyticsTotalsResponse totals,
        AnalyticsTotalsResponse previousTotals,
        List<AppointmentStatusBreakdownResponse> appointmentsByStatus,
        List<DepartmentAnalyticsResponse> departments
) {}
