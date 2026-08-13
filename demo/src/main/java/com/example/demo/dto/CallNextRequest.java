package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** CallNextInput — POST /api/queue/call-next body (BACKEND_SPEC.md §6.4). */
public record CallNextRequest(
        @NotBlank(message = "departmentId is required")
        String departmentId,
        // Optional — omit to call the top of the queue.
        String entryId
) {}
