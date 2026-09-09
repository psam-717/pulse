package com.example.demo.exception;

import java.math.BigDecimal;

/**
 * Raised when a patient tries to move a booking earlier without having paid
 * the reschedule surcharge. Maps to HTTP 402 Payment Required via
 * GlobalExceptionHandler. Thrown before any slot work, so the failed earlier
 * move changes no booking/queue state — the client pays, then retries the
 * exact same reschedule request.
 */
public class SurchargeRequiredException extends RuntimeException {

    private final BigDecimal surchargeAmount;

    public SurchargeRequiredException(BigDecimal surchargeAmount, String message) {
        super(message);
        this.surchargeAmount = surchargeAmount;
    }

    public BigDecimal getSurchargeAmount() {
        return surchargeAmount;
    }
}
