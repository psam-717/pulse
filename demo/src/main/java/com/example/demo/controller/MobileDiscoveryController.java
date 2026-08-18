package com.example.demo.controller;

import com.example.demo.dto.AvailabilityResponse;
import com.example.demo.dto.DepartmentOptionResponse;
import com.example.demo.dto.HospitalCardResponse;
import com.example.demo.service.MobileDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mobile discovery + availability (ARCHITECTURE.md §8 P2 / G4, G5).
 * Patient JWT only — staff tokens receive 403.
 */
@RestController
@RequestMapping("/api/mobile")
public class MobileDiscoveryController {

    private final MobileDiscoveryService mobileDiscoveryService;

    public MobileDiscoveryController(MobileDiscoveryService mobileDiscoveryService) {
        this.mobileDiscoveryService = mobileDiscoveryService;
    }

    @GetMapping("/hospitals")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<HospitalCardResponse>> listHospitals(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(mobileDiscoveryService.listHospitals(lat, lng));
    }

    @GetMapping("/hospitals/{id}/departments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<DepartmentOptionResponse>> listDepartments(@PathVariable Long id) {
        return ResponseEntity.ok(mobileDiscoveryService.listDepartments(id));
    }

    @GetMapping("/departments/{id}/availability")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(mobileDiscoveryService.getAvailability(id, from, days));
    }
}
