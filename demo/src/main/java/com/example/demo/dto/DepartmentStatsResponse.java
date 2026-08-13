package com.example.demo.dto;

/** Web dashboard DepartmentStats shape (BACKEND_SPEC.md §5.3). */
public record DepartmentStatsResponse(
        int total,
        int active,
        int closed,
        int doctorsOnDuty,
        int rooms,
        int waiting
) {}
