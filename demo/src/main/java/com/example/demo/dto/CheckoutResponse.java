package com.example.demo.dto;

/**
 * POST /api/patients/me/payments success — mobile opens checkoutUrl.
 */
public record CheckoutResponse(
        String checkoutUrl,
        String sessionId
) {}
