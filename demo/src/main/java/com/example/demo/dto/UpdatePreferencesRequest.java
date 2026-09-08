package com.example.demo.dto;

/** PATCH /settings/preferences — all fields optional (partial merge). */
public record UpdatePreferencesRequest(
        String language,
        String timezone,
        String dateLocale
) {
}
