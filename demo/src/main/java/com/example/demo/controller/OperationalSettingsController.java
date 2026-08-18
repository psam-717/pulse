package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.settings.OperationalSettingsResponse;
import com.example.demo.dto.settings.UpdateOperationalInput;
import com.example.demo.service.OperationalSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Settings → Operational (BACKEND_SPEC.md §6.7 rows 6-7). GET for any
 * facility staff (queue display logic reads refreshSeconds), PATCH
 * admin-only.
 */
@RestController
@RequestMapping("/api/settings/operational")
public class OperationalSettingsController {

    private final OperationalSettingsService operationalSettingsService;

    public OperationalSettingsController(OperationalSettingsService operationalSettingsService) {
        this.operationalSettingsService = operationalSettingsService;
    }

    @GetMapping
    public ResponseEntity<OperationalSettingsResponse> get() {
        Long facilityId = SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(operationalSettingsService.get(facilityId));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OperationalSettingsResponse> update(@RequestBody UpdateOperationalInput input) {
        Long facilityId = SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(operationalSettingsService.update(facilityId, input));
    }
}
