package com.example.demo.dto.settings;

import java.util.List;

/**
 * PATCH /api/settings/facility body (BACKEND_SPEC.md §5.8). Partial —
 * null fields are left untouched. Deliberately has NO status field: the
 * account status is derived server-side and the client can never set it.
 * hefraDocumentUrl, when provided, is the outcome of the HeFRA document
 * upload (§4.6) and triggers the active_pending_docs → active transition.
 */
public record UpdateFacilityInput(
        String hospitalName,
        String facilityType,
        String region,
        String address,
        String hefraLicense,
        String phone,
        String email,
        List<String> specialties,
        String capacity,
        String logoUrl,
        String hefraDocumentUrl
) {}
