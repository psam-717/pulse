package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.settings.FacilityProfileResponse;
import com.example.demo.dto.settings.UpdateFacilityInput;
import com.example.demo.service.FacilitySettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Settings → Facility (BACKEND_SPEC.md §6.7 rows 1-2). The facility row is
 * the JWT facilityId's Hospital. GET is visible to any facility staff
 * (the status banner renders for everyone); PATCH is admin-only.
 */
@RestController
@RequestMapping("/api/settings/facility")
public class FacilitySettingsController {

    private final FacilitySettingsService facilitySettingsService;

    public FacilitySettingsController(FacilitySettingsService facilitySettingsService) {
        this.facilitySettingsService = facilitySettingsService;
    }

    @GetMapping
    public ResponseEntity<FacilityProfileResponse> get() {
        Long facilityId = SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(facilitySettingsService.get(facilityId));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacilityProfileResponse> update(@RequestBody UpdateFacilityInput input) {
        Long facilityId = SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(facilitySettingsService.update(facilityId, input));
    }
}
