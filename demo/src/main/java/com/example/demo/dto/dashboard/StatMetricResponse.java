package com.example.demo.dto.dashboard;

/**
 * GET /api/dashboard/stats row — BACKEND_SPEC §5.9 (StatMetric). Values are
 * strings so all formatting lives server-side; the trend drives the pill.
 */
public record StatMetricResponse(
        String id,
        String label,
        String value,
        String unit,
        StatTrendResponse trend
) {
    /** Trend pill — direction is raw movement; sentiment is per-metric good/bad. */
    public record StatTrendResponse(
            String direction, // "up" | "down"
            String label,     // e.g. "+12%", "2m", "-5"
            String sentiment  // "positive" | "negative" | "neutral"
    ) {}
}
