package com.example.demo.service;

import com.example.demo.util.GhanaPhoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real SMS delivery for patient OTPs via Arkesel (Ghana).
 *
 * <p>v2 endpoint: {@code POST https://sms.arkesel.com/api/v2/sms/send} with the
 * account API key in the {@code api-key} header and
 * {@code {sender, message, recipients: ["+233…"], sandbox?}} as the JSON body.
 *
 * <p>Delivery is deliberately key-gated so hand-tests never depend on an SMS
 * vendor: real SMS is sent ONLY when an API key AND a sender ID are configured
 * ({@code ARKESEL_API_KEY} / {@code ARKESEL_SENDER_ID}) AND {@code OTP_DEV_MODE}
 * is false. Otherwise the code is logged (dev echo) exactly as before, so the
 * signup/forgot flows keep working on Render with zero credits spent.
 */
@Service
public class ArkeselSmsService {

    private static final Logger log = LoggerFactory.getLogger(ArkeselSmsService.class);
    private static final String SMS_URL = "https://sms.arkesel.com/api/v2/sms/send";

    public enum Purpose {
        VERIFICATION,
        RESET
    }

    private final RestClient restClient;
    private final String apiKey;
    private final String sender;
    private final boolean sandbox;
    private final boolean sendReal;

    public ArkeselSmsService(
            @Value("${arkesel.api-key:}") String apiKey,
            @Value("${arkesel.sender:}") String sender,
            @Value("${arkesel.sandbox:false}") boolean sandbox,
            @Value("${arkesel.send-real:false}") boolean sendReal) {
        this.restClient = RestClient.builder().build();
        this.apiKey = apiKey;
        this.sender = sender;
        this.sandbox = sandbox;
        this.sendReal = sendReal;
    }

    private boolean realDeliveryEnabled() {
        // Independent of otp.dev-mode: dev mode stays ON so staff 2FA and
        // patient flows keep their dev-echo (no staff SMS channel exists yet).
        return sendReal
                && apiKey != null && !apiKey.isBlank()
                && sender != null && !sender.isBlank();
    }

    /**
     * Delivers a one-time code to a patient phone. In dev mode (or without
     * credentials) this only logs the code — same observable behavior as the
     * pre-Arkesel TODO branches, so existing tooling that greps Render logs
     * for the OTP keeps working.
     */
    public void sendOtp(String phone, String otp, Purpose purpose, int expiryMinutes) {
        String intl = GhanaPhoneValidator.toInternational(phone);

        if (!realDeliveryEnabled()) {
            String template = purpose == Purpose.RESET
                    ? "RESET OTP for {}: {} (expires in {} min)"
                    : "OTP for {}: {} (expires in {} min)";
            log.info(template, intl, otp, expiryMinutes);
            return;
        }

        String message = purpose == Purpose.RESET
                ? "Your Pulse password reset code is " + otp
                        + ". It expires in " + expiryMinutes + " minutes."
                : "Your Pulse verification code is " + otp
                        + ". It expires in " + expiryMinutes + " minutes.";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("message", message);
        body.put("recipients", List.of(intl));
        if (sandbox) {
            body.put("sandbox", true); // Arkesel: simulate send, no delivery, no billing
        }

        try {
            var response = restClient.post()
                    .uri(SMS_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Arkesel {} SMS to {} -> {}",
                    purpose == Purpose.RESET ? "reset" : "verification",
                    intl, response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.error("Arkesel SMS send failed for {} ({}): {} body={}",
                    intl, purpose, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "We could not send the SMS code right now. Please try again in a moment.");
        } catch (RestClientException e) {
            log.error("Arkesel SMS send failed for {} ({}): {}", intl, purpose, e.getMessage());
            throw new IllegalStateException(
                    "We could not send the SMS code right now. Please try again in a moment.");
        }
    }
}
