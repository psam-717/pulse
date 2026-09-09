package com.example.demo.dto;

/** Step 2 of the facility staff password reset: email + emailed code. */
public record StaffPasswordResetVerifyRequest(String email, String code) {}
