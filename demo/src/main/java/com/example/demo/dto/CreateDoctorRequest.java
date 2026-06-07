package com.example.demo.dto;

public record CreateDoctorRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String specialization,
        Long departmentId,
        Long hospitalId,
        String licenseNumber,
        String password
) {}