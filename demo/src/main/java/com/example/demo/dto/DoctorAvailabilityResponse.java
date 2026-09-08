package com.example.demo.dto;

/**
 * Public (mobile discovery) doctor summary for
 * {@code GET /api/departments/{departmentId}/doctors}.
 *
 * <p>Deliberately a DTO, not the raw {@code Doctor} entity: the endpoint is
 * unauthenticated, so internals (workspaceId, licenseNumber, duration) must
 * not leak. {@code bookableOnline} mirrors the {@code OnlineBookingSupport}
 * staff-link rule (legacy doctor row whose email maps to a DOCTOR-role staff
 * member of the same facility) — the only doctors a mobile booking can
 * actually resolve to.
 */
public record DoctorAvailabilityResponse(
        Long id,
        String firstName,
        String lastName,
        String specialization,
        String email,
        String phone,
        Long departmentId,
        Long hospitalId,
        boolean bookableOnline
) {}
