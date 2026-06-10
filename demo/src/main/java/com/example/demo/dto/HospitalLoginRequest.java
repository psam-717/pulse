package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record HospitalLoginRequest(
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
