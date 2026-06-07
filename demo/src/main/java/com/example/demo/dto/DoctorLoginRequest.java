package com.example.demo.dto;

public record DoctorLoginRequest(
        String workspaceId,
        String email,
        String password
) {}