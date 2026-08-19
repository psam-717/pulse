package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.MedicalProfileResponse;
import com.example.demo.dto.MedicalRecordsResponse;
import com.example.demo.dto.PatientProfileResponse;
import com.example.demo.dto.UpdateMedicalProfileRequest;
import com.example.demo.dto.UpdatePatientProfileRequest;
import com.example.demo.dto.AddVitalsRequest;
import com.example.demo.service.PatientProfileService;
import com.example.demo.service.PatientRecordsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Patient self-service profile endpoints (ARCHITECTURE.md §8 P1) — mobile app.
 * The patient JWT's subject IS the patient id (JwtAuthFilter stores it as
 * the principal), so every endpoint here is scoped to the caller, never a
 * path id. G1/G2/G13 gaps.
 */
@RestController
@RequestMapping("/api/patients/me")
public class PatientProfileController {

    private final PatientProfileService patientProfileService;
    private final PatientRecordsService patientRecordsService;

    public PatientProfileController(PatientProfileService patientProfileService,
                                    PatientRecordsService patientRecordsService) {
        this.patientProfileService = patientProfileService;
        this.patientRecordsService = patientRecordsService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientProfileResponse> getProfile() {
        return ResponseEntity.ok(patientProfileService.getProfile(currentPatientId()));
    }

    @PatchMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientProfileResponse> updateProfile(
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        return ResponseEntity.ok(patientProfileService.updateProfile(currentPatientId(), request));
    }

    @GetMapping("/medical")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<MedicalProfileResponse> getMedical() {
        return ResponseEntity.ok(patientProfileService.getMedical(currentPatientId()));
    }

    @PatchMapping("/medical")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<MedicalProfileResponse> updateMedical(
            @RequestBody UpdateMedicalProfileRequest request) {
        return ResponseEntity.ok(patientProfileService.updateMedical(currentPatientId(), request));
    }

    @PostMapping("/vitals")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<MedicalProfileResponse> addVitals(
            @RequestBody AddVitalsRequest request) {
        return ResponseEntity.ok(patientProfileService.addVitals(currentPatientId(), request));
    }

    @GetMapping("/records")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<MedicalRecordsResponse> getRecords() {
        return ResponseEntity.ok(patientRecordsService.getRecords(currentPatientId()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
}
