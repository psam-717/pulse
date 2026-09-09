package com.example.demo.dto;

/**
 * POST /api/bookings/{id}/reschedule/surcharge — open one Aza checkout for the
 * GH¢20 earlier-reschedule surcharge on a single booking.
 */
public record RescheduleSurchargePayRequest(
        Long methodId
) {}
