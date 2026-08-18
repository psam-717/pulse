package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * Mobile booking summary — superset of pulse-mobile OutstandingBooking
 * (ARCHITECTURE.md §5.2 / §8 P3). Doctor is never exposed.
 *
 * {@code paymentStatus} on the wire is {@code UNPAID} for the DB value
 * {@code PENDING} so the Payments screen contract is verbatim.
 */
public record BookingSummaryResponse(
        String id,
        String facilityName,
        String department,
        String appointmentDate,
        BigDecimal feeAmount,
        String payByDeadline,
        String status,
        String paymentStatus,
        String startTime
) {}
