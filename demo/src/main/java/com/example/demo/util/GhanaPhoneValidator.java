package com.example.demo.util;

import java.util.regex.Pattern;

/**
 * Ghana mobile number validation (bug-triage BE-13).
 *
 * <p>Accepted forms (whitespace stripped first):
 * <ul>
 *   <li>{@code 0XXXXXXXXX} — leading 0 followed by exactly 10 digits</li>
 *   <li>{@code +233XXXXXXXXX} — country code + 233 followed by exactly 9 digits</li>
 * </ul>
 *
 * <p>Anything else (short/long numbers, letters, other country codes) is
 * rejected with a clear message. Used for signup, profile updates and
 * emergency-contact phone numbers. Login identifiers are NOT validated here —
 * they may be a Ghana Card ID instead of a phone.
 */
public final class GhanaPhoneValidator {

    private GhanaPhoneValidator() {}

    private static final Pattern LOCAL = Pattern.compile("^0\\d{9}$");
    private static final Pattern INTL = Pattern.compile("^\\+233\\d{9}$");

    /** True when {@code raw} is a valid Ghana mobile number (either form). */
    public static boolean isValid(String raw) {
        if (raw == null) return false;
        String s = raw.replaceAll("\\s+", "").replace("-", "");
        return LOCAL.matcher(s).matches() || INTL.matcher(s).matches();
    }

    /**
     * Validates and returns the trimmed phone, throwing
     * {@link IllegalArgumentException} with a user-facing message when invalid.
     */
    public static String requireValid(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String trimmed = raw.trim();
        if (!isValid(trimmed)) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid Ghana number: 0XXXXXXXXX or +233XXXXXXXXX.");
        }
        return trimmed;
    }
}
