package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email delivery for facility staff OTPs via Resend (https://resend.com).
 *
 * <p>v2 endpoint: {@code POST https://api.resend.com/emails} with the account
 * API key as the {@code Authorization: Bearer} header and
 * {@code {from, to[], subject, html}} as the JSON body.
 *
 * <p>Delivery is key-gated exactly like {@link ArkeselSmsService}: real email
 * is sent ONLY when {@code RESEND_API_KEY} AND a from address are configured
 * ({@code RESEND_FROM}) AND {@code EMAIL_REAL} is true. Otherwise the code is
 * logged (dev echo) so the staff forgot-password flow keeps working on Render
 * with zero sends.
 */
@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String EMAILS_URL = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final boolean sendReal;

    public ResendEmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:Pulse <no-reply@pulselabs.tech>}") String from,
            @Value("${resend.send-real:false}") boolean sendReal) {
        this.restClient = RestClient.builder().build();
        this.apiKey = apiKey;
        this.from = from;
        this.sendReal = sendReal;
    }

    private boolean realDeliveryEnabled() {
        // Independent of otp.dev-mode: dev mode stays ON so staff 2FA and
        // reset flows keep their dev-echo while we validate the email path.
        return sendReal
                && apiKey != null && !apiKey.isBlank()
                && from != null && !from.isBlank();
    }

    /**
     * Delivers a one-time reset code to a staff email. In dev mode (or without
     * credentials) this only logs the code — same observable behavior as the
     * login-OTP TODO branches, so Render-log grep tooling keeps working.
     */
    public void sendPasswordResetCode(String to, String otp, int expiryMinutes) {
        if (!realDeliveryEnabled()) {
            log.info("EMAIL RESET OTP for {}: {} (expires in {} min)", to, otp, expiryMinutes);
            return;
        }

        String subject = "Your Pulse password reset code";
        String html = "<p>Hi,</p>"
                + "<p>Your Pulse password reset code is</p>"
                + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:4px\">" + otp + "</p>"
                + "<p>It expires in " + expiryMinutes + " minutes. "
                + "If you didn't request this, you can ignore this email.</p>";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", java.util.List.of(to));
        body.put("subject", subject);
        body.put("html", html);

        try {
            var response = restClient.post()
                    .uri(EMAILS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Resend reset email to {} -> {}", to, response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.error("Resend email send failed for {} ({}): {} body={}",
                    to, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "We could not send the reset code right now. Please try again in a moment.");
        } catch (RestClientException e) {
            log.error("Resend email send failed for {}: {}", to, e.getMessage());
            throw new IllegalStateException(
                    "We could not send the reset code right now. Please try again in a moment.");
        }
    }
}
