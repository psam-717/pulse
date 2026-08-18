package com.example.demo.dto.settings;

/**
 * GET /api/settings/profile → AdminProfile (BACKEND_SPEC.md §5.8).
 * Resolved from the current staff member (JWT principal).
 */
public record AdminProfileResponse(
        String fullName,
        String title,
        String email,
        String phone,
        String avatarUrl,
        NotificationPreferencesDto notificationPreferences
) {
    public record NotificationPreferencesDto(
            boolean emailOnNewAppointment,
            boolean emailOnNoShow,
            boolean smsOnQueueAlert,
            boolean dailySummaryEmail
    ) {}
}
