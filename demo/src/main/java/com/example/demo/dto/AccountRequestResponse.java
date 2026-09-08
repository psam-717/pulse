package com.example.demo.dto;

import com.example.demo.model.StaffAccountRequest;

/** AccountRequest shape (BACKEND_SPEC §6.7 rows 15-16). */
public record AccountRequestResponse(
        String type,
        String requestedAt,
        String status
) {
    /** "none" representation the web contract expects when nothing is pending. */
    public static AccountRequestResponse none() {
        return new AccountRequestResponse("deactivate", "", "none");
    }

    public static AccountRequestResponse from(StaffAccountRequest r) {
        return new AccountRequestResponse(
                r.getType(),
                String.valueOf(r.getRequestedAt()),
                r.getStatus());
    }
}
