package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * Mobile PaymentHistoryEntry shape (payments-store.ts) verbatim.
 */
public record PaymentHistoryEntryResponse(
        String id,
        String facilityName,
        String department,
        String methodLabel,
        String paidDate,
        BigDecimal amount
) {}
