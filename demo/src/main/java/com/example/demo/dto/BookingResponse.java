package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record BookingResponse(
        Long bookingId,
        String patientName,
        String doctorName,
        String departmentName,
        String hospitalName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime bookingDate,
        String status,
        String paymentStatus,
        BigDecimal amountDue
) {}
