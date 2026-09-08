package com.example.demo.dto;

/** PATCH /api/appointments/{id}/payment — admin/doctor marks payment state. */
public record UpdatePaymentRequest(String paymentStatus) {
}
