package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * CreateStaffInput (BACKEND_SPEC.md §5.6). No dutyStatus/accountStatus —
 * every new member starts on_duty / active (§7.6). departmentId "" means
 * unassigned. departmentName is derived server-side from departmentId when
 * it resolves to a department in the caller's facility.
 */
public record CreateStaffRequest(
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Role is required")
        String role,
        @NotBlank(message = "Title is required")
        String title,
        String specialty,
        String departmentId,
        String departmentName,
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        String phone,
        @NotBlank(message = "shiftStart is required")
        String shiftStart,
        @NotBlank(message = "shiftEnd is required")
        String shiftEnd
) {}
