package com.example.demo.payment;

/** Result of creating an Aza (or mock) hosted checkout session. */
public record CheckoutSession(String sessionId, String checkoutUrl) {}
