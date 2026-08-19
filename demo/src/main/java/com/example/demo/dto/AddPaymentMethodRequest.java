package com.example.demo.dto;

/**
 * POST /api/patients/me/payment-methods — display metadata only.
 * last4 is a recognition aid the patient typed; never a full number.
 */
public record AddPaymentMethodRequest(
        String network,
        String label,
        String last4
) {}
