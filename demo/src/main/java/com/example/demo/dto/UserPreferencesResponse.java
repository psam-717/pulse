package com.example.demo.dto;

import com.example.demo.model.StaffPreference;

/** User preferences shape (BACKEND_SPEC §6.7 rows 13-14). */
public record UserPreferencesResponse(
        String language,
        String timezone,
        String dateLocale
) {
    public static UserPreferencesResponse defaults() {
        return new UserPreferencesResponse("en-US", "Africa/Accra", "en-US");
    }

    public static UserPreferencesResponse from(StaffPreference p) {
        return new UserPreferencesResponse(p.getLanguage(), p.getTimezone(), p.getDateLocale());
    }
}
