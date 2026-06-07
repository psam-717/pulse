package com.example.demo.dto;

public record BookingRequest(
        Long patientId,
        Long timeSlotId
) {}
