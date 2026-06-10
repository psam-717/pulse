package com.example.demo.dto;

public record HospitalResponse(
        Long id,
        String name,
        String licenseNumber,
        String licenseDocumentUrl,
        String address,
        Double latitude,
        Double longitude,
        String specialties,
        Integer capacity,
        String phone,
        String email,
        String verificationStatus,
        String rejectionReason,
        String createdAt
) {}
