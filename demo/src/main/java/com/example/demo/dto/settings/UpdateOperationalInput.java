package com.example.demo.dto.settings;

import java.util.List;

/**
 * PATCH /api/settings/operational body (BACKEND_SPEC.md §5.8). Partial —
 * null fields are left untouched; notificationDefaults merges per-field.
 * queuePriorityLevels, when provided, replaces the whole list (ids must
 * stay within the known set: emergency | urgent | routine).
 */
public record UpdateOperationalInput(
        List<QueuePriorityLevelDto> queuePriorityLevels,
        Integer queueRefreshSeconds,
        Integer appointmentSlotMinutes,
        Integer noShowGraceMinutes,
        NotificationDefaultsInput notificationDefaults
) {
    public record QueuePriorityLevelDto(String id, String label, int weight) {}

    public record NotificationDefaultsInput(
            Boolean sendPatientEmailConfirmations,
            Boolean sendPatientSmsReminders
    ) {}
}
