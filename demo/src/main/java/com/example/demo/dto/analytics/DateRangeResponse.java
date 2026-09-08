package com.example.demo.dto.analytics;

/** Inclusive date range — §5.10 (DateRange). */
public record DateRangeResponse(
        String from,   // YYYY-MM-DD
        String to      // YYYY-MM-DD
) {}
