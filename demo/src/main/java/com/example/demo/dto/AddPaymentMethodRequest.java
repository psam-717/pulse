package com.example.demo.dto;

/**
 * POST /api/patients/me/payment-methods.
 *
 * <p>Two shapes:
 * <ul>
 *   <li><b>Mobile money</b> ({@code mtn_momo} / {@code telecel_cash}): send
 *       {@code accountNumber} — the full 10-digit wallet number
 *       (0XXXXXXXXX or +233XXXXXXXXX). The wallet number is the method's
 *       identifier (a phone number, not a secret PAN) and is displayed in
 *       full on the Payments screen.</li>
 *   <li><b>Card</b>: send {@code last4} only — a display aid; never a PAN,
 *       PIN or CVV.</li>
 * </ul>
 * {@code label} is optional; the server builds one when omitted.
 */
public record AddPaymentMethodRequest(
        String network,
        String label,
        String last4,
        String accountNumber
) {}
