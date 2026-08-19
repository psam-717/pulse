package com.example.demo.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Dev fallback when {@code AZA_API_KEY} is unset. Returns a fake
 * {@code cs_test_mock_...} session; {@code POST /api/webhooks/aza} with
 * that session id completes the payment locally.
 */
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public CheckoutSession createSession(long amountMinor, String currency) {
        String sessionId = "cs_test_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String url = "https://pay.aza.systems/c/" + sessionId;
        log.info("Mock Aza session {} for {} {} (no AZA_API_KEY)", sessionId, amountMinor, currency);
        return new CheckoutSession(sessionId, url);
    }
}
