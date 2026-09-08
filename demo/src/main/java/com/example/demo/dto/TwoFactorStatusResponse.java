package com.example.demo.dto;

/** GET/PATCH /settings/2fa → { "enabled": bool }. */
public record TwoFactorStatusResponse(boolean enabled) {
}
