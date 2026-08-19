package com.example.demo.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts Pulse GHS ({@code Booking.amountDue}, e.g. 20.00) to the integer
 * Aza {@code amount} field.
 *
 * <p>Public Aza docs (aza.systems/developers) charge ₵50.00 as
 * {@code "amount": 5000} — pesewas, i.e. minor units. Confirmed from the
 * landing example; the login-walled API explorer was not used (no key in
 * this packet). Convert exactly once, here.
 */
public final class AzaAmountConverter {

    private AzaAmountConverter() {}

    /** GHS → pesewas. 20.00 → 2000. */
    public static long toMinorUnits(BigDecimal ghs) {
        if (ghs == null) {
            throw new IllegalArgumentException("Amount in GHS is required.");
        }
        return ghs.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Pesewas → GHS. 2000 → 20.00. */
    public static BigDecimal toGhs(long minorUnits) {
        return BigDecimal.valueOf(minorUnits).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }
}
