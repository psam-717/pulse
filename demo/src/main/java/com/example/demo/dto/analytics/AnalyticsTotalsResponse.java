package com.example.demo.dto.analytics;

/** Period totals — §5.10 (AnalyticsTotals). */
public record AnalyticsTotalsResponse(
        int patientVolume,
        int avgWaitMinutes,
        int p90WaitMinutes,
        int served,
        double noShowRate      // 0-100
) {}
