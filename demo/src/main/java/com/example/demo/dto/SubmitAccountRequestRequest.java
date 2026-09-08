package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /settings/account-request — danger zone (deactivate/delete). */
public record SubmitAccountRequestRequest(
        @NotBlank String type,
        String transferOwnershipTo
) {
}
