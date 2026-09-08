package com.example.demo.dto;

/**
 * Patient live queue ticket — GET /api/queue/me.
 * Field names match pulse-mobile stores/queue-store.ts exactly.
 */
public record QueueTicketResponse(
        String hospitalName,
        String department,
        String doctorName,
        int currentNumber,
        int userNumber,
        int waitTimeMins,
        String roomNumber,
        String estimatedTime,
        Long bookingId,          // null for walk-ins / name-matched entries
        String bookingReference, // e.g. APT-0049 — null when no booking
        int queueTotal,          // people still in line in this department (WAITING + IN_CONSULTATION, incl. patient)
        int aheadCount,          // active people before the patient in the same department
        int servedCount          // people checked in before the patient who already completed/no-showed/skipped
) {}
