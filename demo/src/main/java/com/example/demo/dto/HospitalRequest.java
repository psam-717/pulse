package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HospitalRequest(

        @NotBlank(message = "Hospital name is required")
        String name,

        @NotBlank(message = "License number is required")
        String licenseNumber,

        String licenseDocumentUrl,

        @NotBlank(message = "Address is required")
        String address,

        Double latitude,
        Double longitude,

        String specialties,        // JSON array, e.g. ["Cardiology","Pediatrics"]

        Integer capacity,

        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        // Primary admin fields
        @NotBlank(message = "Admin full name is required")
        String adminFullName,

        @NotBlank(message = "Admin email is required")
        @Email(message = "Invalid admin email format")
        String adminEmail,

        @NotBlank(message = "Admin password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String adminPassword,

        @NotBlank(message = "Admin phone is required")
        String adminPhone
) {}
