package com.example.demo.dto;

/**
 * Mobile PaymentMethod shape (payments-store.ts) verbatim.
 */
public record PaymentMethodResponse(
        String id,
        String network,
        String label,
        String last4,
        String gatewayToken,
        boolean isDefault
) {}
