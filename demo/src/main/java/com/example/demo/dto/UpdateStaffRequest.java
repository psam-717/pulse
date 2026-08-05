package com.example.demo.dto;

/**
 * UpdateStaffInput body (BACKEND_SPEC.md §5.6) — all fields optional shallow
 * patch. Also the mechanism for account activate/deactivate via
 * {@code accountStatus} (§7.6). Enum values are validated when present.
 */
public record UpdateStaffRequest(
        String name,
        String role,
        String title,
        String specialty,
        String departmentId,
        String departmentName,
        String email,
        String phone,
        String shiftStart,
        String shiftEnd,
        String dutyStatus,
        String accountStatus
) {}
