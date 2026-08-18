package com.example.demo.service;

import com.example.demo.dto.settings.FacilityProfileResponse;
import com.example.demo.dto.settings.UpdateFacilityInput;
import com.example.demo.model.Hospital;
import com.example.demo.model.OperationalSettings;
import com.example.demo.model.VerificationStatus;
import com.example.demo.repository.HospitalRepository;
import com.example.demo.repository.OperationalSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Settings → Facility (BACKEND_SPEC.md §5.8 / §6.7 rows 1-2).
 *
 * Maps the legacy {@link Hospital} row (identified by the JWT facilityId)
 * to the frontend's FacilityProfile shape. The account status is DERIVED
 * server-side and the client can never set it:
 *
 *   - verification REJECTED                       → "suspended"
 *   - HeFRA document uploaded (licenseDocumentUrl) → "active"
 *   - otherwise                                   → "active_pending_docs"
 *
 * Uploading the HeFRA document (§4.6) flows through
 * {@link #update(Long, UpdateFacilityInput)} with hefraDocumentUrl set —
 * the server applies the active_pending_docs → active transition.
 */
@Service
public class FacilitySettingsService {

    private final HospitalRepository hospitalRepository;
    private final OperationalSettingsRepository operationalSettingsRepository;

    public FacilitySettingsService(HospitalRepository hospitalRepository,
                                   OperationalSettingsRepository operationalSettingsRepository) {
        this.hospitalRepository = hospitalRepository;
        this.operationalSettingsRepository = operationalSettingsRepository;
    }

    public FacilityProfileResponse get(Long facilityId) {
        return toResponse(requireFacility(facilityId), facilityId);
    }

    @Transactional
    public FacilityProfileResponse update(Long facilityId, UpdateFacilityInput input) {
        Hospital h = requireFacility(facilityId);

        if (input.hospitalName() != null) h.setName(input.hospitalName());
        if (input.facilityType() != null) h.setFacilityType(input.facilityType());
        if (input.region() != null) h.setRegion(input.region());
        if (input.address() != null) h.setAddress(input.address());
        if (input.hefraLicense() != null) h.setLicenseNumber(input.hefraLicense());
        if (input.phone() != null) h.setPhone(input.phone());
        if (input.email() != null) h.setEmail(input.email());
        if (input.specialties() != null) h.setSpecialties(serializeSpecialties(input.specialties()));
        if (input.capacity() != null) h.setCapacity(parseCapacity(input.capacity()));
        if (input.logoUrl() != null) h.setLogoUrl(input.logoUrl());

        // HeFRA document upload outcome (§4.6): the doc clears the grace
        // period. Never trust a client-sent status — derive it from state.
        if (input.hefraDocumentUrl() != null && !input.hefraDocumentUrl().isBlank()) {
            h.setLicenseDocumentUrl(input.hefraDocumentUrl());
            if (h.getVerificationStatus() != VerificationStatus.REJECTED) {
                h.setVerificationStatus(VerificationStatus.APPROVED);
            }
        }

        hospitalRepository.save(h);
        return toResponse(h, facilityId);
    }

    // ---- mapping helpers ----

    private Hospital requireFacility(Long facilityId) {
        return hospitalRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found for id " + facilityId));
    }

    private FacilityProfileResponse toResponse(Hospital h, Long facilityId) {
        String status = deriveStatus(h);
        String hefraDueDate = "active_pending_docs".equals(status)
                ? h.getCreatedAt().toLocalDate().plusDays(90).toString()
                : null;

        int slotMinutes = operationalSettingsRepository.findByFacilityId(facilityId)
                .map(OperationalSettings::getAppointmentSlotMinutes)
                .orElse(20);

        return new FacilityProfileResponse(
                h.getName(),
                h.getFacilityType(),
                status,
                hefraDueDate,
                h.getLicenseDocumentUrl(),
                h.getRegion(),
                h.getAddress(),
                h.getLicenseNumber(),
                h.getPhone(),
                h.getEmail(),
                parseSpecialties(h.getSpecialties()),
                h.getCapacity() != null ? String.valueOf(h.getCapacity()) : null,
                String.valueOf(slotMinutes),
                defaultOperatingHours(),
                h.getLogoUrl());
    }

    /** Status is a server-side derivation, never client input (spec §5.8). */
    static String deriveStatus(Hospital h) {
        if (h.getVerificationStatus() == VerificationStatus.REJECTED) {
            return "suspended";
        }
        boolean hasDoc = h.getLicenseDocumentUrl() != null && !h.getLicenseDocumentUrl().isBlank();
        return hasDoc ? "active" : "active_pending_docs";
    }

    /** Hospital.specialties is a JSON string like ["Cardiology","Pediatrics"]. */
    private List<String> parseSpecialties(String json) {
        if (json == null || json.isBlank()) return List.of();
        String cleaned = json.replace("[", "").replace("]", "").replace("\"", "");
        if (cleaned.isBlank()) return List.of();
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String serializeSpecialties(List<String> specialties) {
        return specialties.stream()
                .map(s -> "\"" + s.trim() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private Integer parseCapacity(String capacity) {
        try {
            return Integer.parseInt(capacity.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("capacity must be a whole number, got: " + capacity);
        }
    }

    /** No WorkingHours mapping exists on the legacy Hospital — mirror the mock default. */
    private FacilityProfileResponse.OperatingHoursDto defaultOperatingHours() {
        return new FacilityProfileResponse.OperatingHoursDto(false, List.of(
                new FacilityProfileResponse.ScheduleDto(
                        "facility-default",
                        List.of("Mon", "Tue", "Wed", "Thu", "Fri"),
                        "08:00", "17:00")));
    }
}
