package com.example.demo.dto;

/**
 * PATCH /api/bookings/{id}/reschedule — move to a free department slot.
 */
public record RescheduleRequest(
        String newDate,
        String newTime
) {}
