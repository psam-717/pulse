package com.example.demo.dto.settings;

import java.util.List;

/**
 * GET /api/settings/facility → FacilityProfile (BACKEND_SPEC.md §5.8).
 * status is DERIVED server-side (never echoed from client input) — see
 * FacilitySettingsService.deriveStatus().
 */
public record FacilityProfileResponse(
        String hospitalName,
        String facilityType,
        String status,
        String hefraDueDate,
        String hefraDocumentUrl,
        String region,
        String address,
        String hefraLicense,
        String phone,
        String email,
        List<String> specialties,
        String capacity,
        String duration,
        OperatingHoursDto operatingHours,
        String logoUrl
) {
    public record OperatingHoursDto(boolean alwaysOpen, List<ScheduleDto> schedules) {}

    public record ScheduleDto(String id, List<String> days, String open, String close) {}
}
