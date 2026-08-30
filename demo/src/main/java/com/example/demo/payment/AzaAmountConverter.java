package com.example.demo.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts Pulse GHS ({@code Booking.amountDue}, e.g. 20.00) to the integer
 * {@code amount} field Aza expects on {@code POST /api/v1/merchant/sessions}.
 *
 * <p><b>Unit semantics — empirically verified 2026-08-30:</b> Aza's
 * {@code amount} field is <b>GHS major units</b>, NOT pesewas. Probe:
 * creating a session with {@code amount: 1} renders "GH₵ 1.00" on the hosted
 * checkout (pay.aza.systems/c/...) — if it were pesewas it would render
 * "GH₵ 0.01". The public docs example ("Charge a customer ₵50.00" →
 * {@code {"amount": 5000}}) is therefore WRONG: that request actually charges
 * ₵5,000.00. First reported to Aza by Pulse (bug-triage BE-5); their docs
 * still show the incorrect example. Convert exactly once, here.
 */
public final class AzaAmountConverter {

    private AzaAmountConverter() {}

    /** Pulse GHS → Aza {@code amount}. 20.00 → 20. Whole cedis, HALF_UP. */
    public static long toAzaAmount(BigDecimal ghs) {
        if (ghs == null) {
            throw new IllegalArgumentException("Amount in GHS is required.");
        }
        return ghs.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
