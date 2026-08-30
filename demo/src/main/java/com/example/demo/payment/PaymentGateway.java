package com.example.demo.payment;

/**
 * Hosted-checkout gateway. Implementations must never see raw card/MoMo numbers.
 */
public interface PaymentGateway {

    /**
     * Create a hosted checkout session.
     *
     * @param amount   Aza's {@code amount} field — GHS major units, NOT pesewas
     *                 (empirically verified Aug 2026; see {@link AzaAmountConverter}).
     * @param currency ISO currency code, e.g. "GHS"
     */
    CheckoutSession createSession(long amount, String currency);
}
