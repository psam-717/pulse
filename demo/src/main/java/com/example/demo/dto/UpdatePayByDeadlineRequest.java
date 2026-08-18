package com.example.demo.dto;

/**
 * Staff-only: set a booking's pay-by deadline (support / verification).
 * Patients cannot extend or shorten their own window.
 */
public record UpdatePayByDeadlineRequest(String payByDeadline) {}
