package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.InsuranceDetailsResponse;
import com.example.demo.dto.UpdateInsuranceRequest;
import com.example.demo.service.InsuranceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Patient self-service insurance (ARCHITECTURE.md §8 P2 / G3).
 * Patient JWT subject is the patient id — never a path id.
 */
@RestController
@RequestMapping("/api/patients/me/insurance")
public class InsuranceController {

    private final InsuranceService insuranceService;

    public InsuranceController(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<InsuranceDetailsResponse> getInsurance() {
        return ResponseEntity.ok(insuranceService.getInsurance(currentPatientId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<InsuranceDetailsResponse> upsertInsurance(
            @RequestBody UpdateInsuranceRequest request) {
        return ResponseEntity.ok(insuranceService.upsertInsurance(currentPatientId(), request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
}
