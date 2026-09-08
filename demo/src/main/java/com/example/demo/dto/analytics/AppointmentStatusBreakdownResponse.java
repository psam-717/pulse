package com.example.demo.dto.analytics;

/** Appointment outcome breakdown — §5.10 (AppointmentStatusBreakdown). */
public record AppointmentStatusBreakdownResponse(
        String status,   // scheduled | confirmed | checked_in | completed | cancelled | no_show
        String label,
        int count
) {}
