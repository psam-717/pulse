package com.example.demo.dto;

public record PatientLoginRequest(
        String identifier,
        String password
) {}