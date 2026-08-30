package com.example.demo.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Live Aza merchant checkout: {@code POST /api/v1/merchant/sessions}.
 * Auth is {@code X-Api-Key}. Amount is GHS major units, NOT pesewas
 * (see {@link AzaAmountConverter}; bug-triage BE-5).
 */
public class AzaPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(AzaPaymentGateway.class);

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public AzaPaymentGateway(String baseUrl, String apiKey) {
        this.apiKey = apiKey;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public CheckoutSession createSession(long amountMinor, String currency) {
        String raw;
        try {
            raw = http.post()
                    .uri("/api/v1/merchant/sessions")
                    .header("X-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountMinor, "currency", currency))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Aza checkout failed: " + ex.getMessage()
                            + ". Check AZA_API_KEY and https://api.aza.systems health.");
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Aza returned an empty checkout response.");
        }
        try {
            JsonNode n = mapper.readTree(raw);
            String sessionId = firstText(n, "id", "sessionId", "session_id");
            String url = firstText(n, "url", "checkoutUrl", "checkout_url", "link");
            if (sessionId == null) {
                throw new IllegalStateException(
                        "Aza response is missing a session id. Body keys were not id/sessionId.");
            }
            if (url == null) {
                url = "https://pay.aza.systems/c/" + sessionId;
            }
            log.info("Aza session {} created ({} {})", sessionId, amountMinor, currency);
            return new CheckoutSession(sessionId, url);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Aza checkout response.");
        }
    }

    private static String firstText(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        JsonNode data = n.get("data");
        if (data != null && data.isObject()) {
            for (String k : keys) {
                JsonNode v = data.get(k);
                if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
            }
        }
        return null;
    }
}
