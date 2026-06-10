package com.example.demo.dto;

public record RegistrationResponse(
        String status,
        String message,
        Long hospitalId,
        Long adminId,
        String token
) {}
