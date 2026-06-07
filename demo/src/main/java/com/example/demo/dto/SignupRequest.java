package com.example.demo.dto;

public record SignupRequest(
        String fullName,
        String phone,
        String password,
        String ghanaCard
) {}