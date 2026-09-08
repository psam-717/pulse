package com.example.demo.dto;

import com.example.demo.model.StaffSession;

import java.time.Instant;

/** Web session shape (BACKEND_SPEC §5.9 / §6.7 rows 8-10). */
public record ActiveSessionResponse(
        String id,
        String device,
        String browser,
        String location,
        String lastActive,
        boolean current
) {
    public static ActiveSessionResponse from(StaffSession s, String currentSid) {
        return new ActiveSessionResponse(
                s.getId(),
                s.getDevice(),
                s.getBrowser(),
                s.getLocation(),
                String.valueOf(s.getLastActive()),
                s.getId().equals(currentSid));
    }
}
