package com.example.demo.dto;

import com.example.demo.model.StaffInvite;

/** RoleInvite shape (BACKEND_SPEC §6.7 rows 17-19). */
public record RoleInviteResponse(
        String id,
        String email,
        String role,
        String invitedAt,
        String status
) {
    public static RoleInviteResponse from(StaffInvite invite) {
        return new RoleInviteResponse(
                invite.getPublicId(),
                invite.getEmail(),
                invite.getRole().toLowerCase(),
                String.valueOf(invite.getInvitedAt()),
                invite.getStatus());
    }
}
