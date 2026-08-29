package com.example.demo.model;

/** Live queue entry status — BACKEND_SPEC.md §5.4. */
public enum QueueStatus {
    WAITING,
    IN_CONSULTATION,
    COMPLETED,
    NO_SHOW,
    SKIPPED,
    CANCELLED
}
