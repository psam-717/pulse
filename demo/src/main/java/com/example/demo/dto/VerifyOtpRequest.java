package com.example.demo.dto;

public record VerifyOtpRequest(
        String phone,
        String otp
) {}
