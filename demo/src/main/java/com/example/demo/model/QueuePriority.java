package com.example.demo.model;

/** Queue priority — BACKEND_SPEC.md §5.4; ordering rank: emergency > urgent > routine (§7.2). */
public enum QueuePriority {
    ROUTINE,
    URGENT,
    EMERGENCY
}
