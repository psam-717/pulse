package com.example.demo.dto;

/**
 * POST /api/bookings/mobile — book by department + date + time.
 * The patient JWT is authoritative; {@code patientId} is ignored if present.
 */
public record MobileBookingRequest(
        Long departmentId,
        String date,
        String time,
        Long patientId
) {}
