package com.example.demo.dto;

/** Patient's own booking in the mobile "Bookings" section (approved/cancelled…). */
public record PatientBookingResponse(
        String id,
        String reference,
        String hospitalName,
        String departmentName,
        String doctorName,
        String scheduledAt,
        String status,          // scheduled | confirmed | checked_in | completed | cancelled | no_show
        String paymentStatus    // pending | paid | failed | refunded
) {}
