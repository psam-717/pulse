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
        String estimatedTime
) {}
