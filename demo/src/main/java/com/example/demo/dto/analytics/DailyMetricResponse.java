package com.example.demo.dto.analytics;

/** One day's rollup across a department or the whole facility — §5.10 (DailyMetric). */
public record DailyMetricResponse(
        String date,           // YYYY-MM-DD
        int appointments,
        int walkIns,
        int patientVolume,     // appointments + walkIns
        int avgWaitMinutes,
        int p90WaitMinutes,
        int served,            // queue throughput (called into consultation)
        int noShows,
        double noShowRate      // 0-100
) {}
