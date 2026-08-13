package com.example.demo.dto;

import com.example.demo.model.StaffMember;

/**
 * The web dashboard's session shape — mirrors {@code WorkspaceSession}
 * in the frontend (lib/types/auth.ts). Staff ids and facility ids are
 * exposed as strings to match the frontend contract exactly.
 */
public record WorkspaceSessionResponse(
        String staffId,
        String role,
        String name,
        String email,
        String facilityId,
        String departmentId,
        String departmentName,
        String title,
        String specialty,
        String avatarUrl
) {
    public static WorkspaceSessionResponse from(StaffMember s) {
        return new WorkspaceSessionResponse(
                String.valueOf(s.getId()),
                s.getRole().name().toLowerCase(),
                s.getName(),
                s.getEmail(),
                String.valueOf(s.getFacilityId()),
                s.getDepartmentId(),
                s.getDepartmentName(),
                s.getTitle(),
                s.getSpecialty(),
                s.getAvatarUrl()
        );
    }
}
