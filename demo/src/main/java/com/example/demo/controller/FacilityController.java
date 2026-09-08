package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.FacilitySummaryResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Hospital;
import com.example.demo.repository.HospitalRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The calling staff member's facility (BACKEND_SPEC.md §5.9 FacilitySummary). */
@RestController
@RequestMapping("/api/facility")
public class FacilityController {

    private final HospitalRepository hospitalRepository;

    public FacilityController(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<FacilitySummaryResponse> current() {
        Long facilityId = SecurityUtils.requireFacilityId();
        Hospital hospital = hospitalRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
        return ResponseEntity.ok(new FacilitySummaryResponse(
                String.valueOf(hospital.getId()), hospital.getName()));
    }
}
