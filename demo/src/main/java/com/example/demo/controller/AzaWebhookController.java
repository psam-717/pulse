package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * Aza checkout webhook (permit-all). Public docs do not publish a
 * signature scheme — we require the session id to exist in our DB.
 * Optional {@code AZA_WEBHOOK_SECRET} is checked against
 * {@code X-Aza-Signature} / {@code X-Webhook-Secret} when set.
 */
@RestController
@RequestMapping("/api/webhooks/aza")
public class AzaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AzaWebhookController.class);

    private final PaymentService paymentService;
    private final String webhookSecret;

    public AzaWebhookController(PaymentService paymentService,
                                @Value("${aza.webhook-secret:}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    @PostMapping
    public ResponseEntity<ApiResponse> receive(
            @RequestHeader(value = "X-Aza-Signature", required = false) String azaSig,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookHeader,
            @RequestBody(required = false) JsonNode body) {
        if (!webhookSecret.isBlank()) {
            String provided = azaSig != null && !azaSig.isBlank() ? azaSig : webhookHeader;
            if (provided == null || !webhookSecret.equals(provided)) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error(401,
                                "Invalid webhook secret. Check X-Aza-Signature / X-Webhook-Secret."));
            }
        }

        String type = text(body, "type");
        String sessionId = extractSessionId(body);
        if (sessionId == null) {
            throw new IllegalArgumentException(
                    "sessionId is required. Send the Aza checkout id (cs_...) as sessionId or data.id.");
        }
        if (type != null && !type.isBlank()
                && !type.toLowerCase().contains("completed")
                && !type.toLowerCase().contains("success")) {
            log.info("Ignoring Aza webhook type={} session={}", type, sessionId);
            return ResponseEntity.ok(ApiResponse.success("Ignored event type " + type));
        }

        boolean first = paymentService.completeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(
                first ? "Payment completed" : "Duplicate webhook ignored"));
    }

    private static String extractSessionId(JsonNode body) {
        if (body == null || body.isNull()) return null;
        for (String k : new String[]{"sessionId", "session_id", "id"}) {
            String v = text(body, k);
            if (v != null && !v.isBlank()) return v;
        }
        JsonNode data = body.get("data");
        if (data != null && data.isObject()) {
            for (String k : new String[]{"sessionId", "session_id", "id"}) {
                String v = text(data, k);
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}
