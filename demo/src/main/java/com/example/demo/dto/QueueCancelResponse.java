package com.example.demo.dto;

/**
 * POST /api/queue/me/cancel — patient cancels their own live queue ticket.
 */
public record QueueCancelResponse(
        String message,
        String ticketNumber
) {}
