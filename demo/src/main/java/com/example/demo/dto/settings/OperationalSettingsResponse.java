package com.example.demo.dto.settings;

import java.util.List;

/**
 * GET /api/settings/operational → OperationalSettings (BACKEND_SPEC.md §5.8).
 * queuePriorityLevels mirrors the frontend mock seed: Emergency 3,
 * Urgent 2, Routine 1 (same weights as QueuePriority enum).
 */
public record OperationalSettingsResponse(
        List<QueuePriorityLevelDto> queuePriorityLevels,
        int queueRefreshSeconds,
        int appointmentSlotMinutes,
        int noShowGraceMinutes,
        NotificationDefaultsDto notificationDefaults
) {
    public record QueuePriorityLevelDto(String id, String label, int weight) {}

    public record NotificationDefaultsDto(
            boolean sendPatientEmailConfirmations,
            boolean sendPatientSmsReminders
    ) {}
}
