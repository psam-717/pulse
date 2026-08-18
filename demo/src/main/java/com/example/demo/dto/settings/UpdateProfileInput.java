package com.example.demo.dto.settings;

/**
 * PATCH /api/settings/profile body (BACKEND_SPEC.md §5.8). Partial —
 * null fields are left untouched; notificationPreferences merges
 * per-field (only non-null booleans applied).
 */
public record UpdateProfileInput(
        String fullName,
        String title,
        String phone,
        String avatarUrl,
        NotificationPreferencesInput notificationPreferences
) {
    public record NotificationPreferencesInput(
            Boolean emailOnNewAppointment,
            Boolean emailOnNoShow,
            Boolean smsOnQueueAlert,
            Boolean dailySummaryEmail
    ) {}
}
