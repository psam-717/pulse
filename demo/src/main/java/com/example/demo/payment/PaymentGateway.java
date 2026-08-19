package com.example.demo.payment;

/**
 * Hosted-checkout gateway. Implementations must never see raw card/MoMo numbers.
 */
public interface PaymentGateway {

    CheckoutSession createSession(long amountMinor, String currency);
}
