package com.example.demo.dto;

/** Light department list for the appointments filter (BACKEND_SPEC.md §5.2). */
public record AppointmentDepartmentResponse(
        String id,
        String name
) {}
