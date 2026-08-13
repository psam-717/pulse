package com.example.demo.dto;

/**
 * Web dashboard Appointment shape (BACKEND_SPEC.md §5.2) — a projection over
 * the Booking entity (the mobile booking IS the facility appointment; D4).
 */
public record AppointmentResponse(
        String id,
        String reference,
        String patientId,
        String patientName,
        String departmentId,
        String departmentName,
        String doctorName,
        String scheduledAt,       // ISO LocalDateTime
        int durationMinutes,
        String status,
        String type,
        String priority,
        String reason
) {}
