package com.example.demo.dto;

/** Live-queue per-department summary (BACKEND_SPEC.md §5.4). */
public record QueueDepartmentResponse(
        String id,
        String name,
        int waiting,
        String nowServing,
        int longestWaitMinutes,
        String severity
) {}
