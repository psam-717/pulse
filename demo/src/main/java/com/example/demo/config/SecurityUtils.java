package com.example.demo.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads facility-plane context from the authenticated principal.
 *
 * JwtAuthFilter stores the staff id as the principal and the JWT's
 * facilityId claim as the credentials (Long) for staff tokens. Legacy
 * patient/hospital tokens carry neither — callers must use
 * {@link #requireFacilityId()} to enforce the tenant boundary
 * (BACKEND_SPEC.md §2.2: a cross-tenant leak is a breach).
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** Staff id (principal), or null for legacy/non-staff tokens. */
    public static Long currentStaffId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }

    /** facilityId claim from the JWT, or null for non-staff tokens. */
    public static Long currentFacilityId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Long facilityId) {
            return facilityId;
        }
        return null;
    }

    /** Like {@link #currentFacilityId()} but throws 403 when absent. */
    public static Long requireFacilityId() {
        Long facilityId = currentFacilityId();
        if (facilityId == null) {
            throw new AccessDeniedException(
                    "Facility context required — this endpoint needs a staff token bound to a facility");
        }
        return facilityId;
    }

    /** Like {@link #currentStaffId()} but throws 403 when absent. */
    public static Long requireStaffId() {
        Long staffId = currentStaffId();
        if (staffId == null) {
            throw new AccessDeniedException(
                    "Staff identity required — this endpoint needs a staff token");
        }
        return staffId;
    }
}
