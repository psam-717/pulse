package com.example.demo.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uses the real Aza client only when {@code AZA_API_KEY} is set.
 * Otherwise the mock gateway keeps local/dev usable with no secrets.
 */
@Configuration
public class PaymentGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfig.class);

    @Bean
    public PaymentGateway paymentGateway(
            @Value("${aza.api-key:}") String apiKey,
            @Value("${aza.base-url:https://api.aza.systems}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AZA_API_KEY is unset — using MockPaymentGateway (no real money moves)");
            return new MockPaymentGateway();
        }
        log.info("Aza PaymentGateway enabled against {}", baseUrl);
        return new AzaPaymentGateway(baseUrl, apiKey);
    }
}
