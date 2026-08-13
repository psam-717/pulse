package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** UpdateQueueStatusRequest — PATCH /api/queue/entries/{id} body. */
public record UpdateQueueStatusRequest(
        @NotBlank(message = "status is required")
        @Pattern(regexp = "waiting|in_consultation|completed|no_show|skipped",
                message = "status must be one of: waiting, in_consultation, completed, no_show, skipped")
        String status
) {}
