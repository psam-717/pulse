package com.example.demo.dto;

import java.math.BigDecimal;

/**
 * HTTP 402 body returned when an earlier reschedule still owes the GH¢20
 * surcharge: {@code {code, surchargeAmount, message}}. The client surfaces
 * the amount, collects payment via POST /api/bookings/{id}/reschedule/surcharge,
 * then retries the reschedule.
 */
public record SurchargeRequiredResponse(
        String code,
        BigDecimal surchargeAmount,
        String message
) {}
