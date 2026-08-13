package com.example.demo.dto;

import com.example.demo.model.StaffMember;

/**
 * Web dashboard StaffMember shape (BACKEND_SPEC.md §5.6). Role/duty/account
 * enums are exposed lowercase to match the frontend contract exactly.
 */
public record StaffResponse(
        String id,
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
        String accountStatus,
        String avatarUrl
) {
    public static StaffResponse from(StaffMember s) {
        return new StaffResponse(
                String.valueOf(s.getId()),
                s.getName(),
                s.getRole().name().toLowerCase(),
                s.getTitle(),
                s.getSpecialty(),
                s.getDepartmentId(),
                s.getDepartmentName(),
                s.getEmail(),
                s.getPhone(),
                s.getShiftStart(),
                s.getShiftEnd(),
                s.getDutyStatus().name().toLowerCase(),
                s.getAccountStatus().name().toLowerCase(),
                s.getAvatarUrl()
        );
    }
}
