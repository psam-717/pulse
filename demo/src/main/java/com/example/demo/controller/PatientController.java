package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.BookingResponse;
import com.example.demo.dto.CreatePatientRequest;
import com.example.demo.dto.PatientResponse;
import com.example.demo.dto.RecordVitalsRequest;
import com.example.demo.dto.UpdateClinicalRecordRequest;
import com.example.demo.dto.UpdatePatientRequest;
import com.example.demo.service.BookingService;
import com.example.demo.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK')";
    private static final String READ_ROLES = "hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')";

    private final BookingService bookingService;
    private final PatientService patientService;

    public PatientController(BookingService bookingService, PatientService patientService) {
        this.bookingService = bookingService;
        this.patientService = patientService;
    }

    @GetMapping("/me/bookings")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<BookingResponse>> listMyBookings(
            @PageableDefault(size = 20) Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long patientId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(bookingService.listPatientBookings(patientId, pageable));
    }

    // ===== Facility-plane (web dashboard) — patient directory (BACKEND_SPEC §5.5) =====

    /** Full patient directory — powers /d/patients, /w/patients and the global search. */
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<PatientResponse>> listPatients() {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PatientResponse> getPatient(@PathVariable Long id) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.get(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable Long id, @RequestBody UpdatePatientRequest request) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.update(id, request));
    }

    @PatchMapping("/{id}/clinical-record")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PatientResponse> updateClinicalRecord(
            @PathVariable Long id, @RequestBody UpdateClinicalRecordRequest request) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.updateClinicalRecord(id, request));
    }

    @PostMapping("/{id}/vitals")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PatientResponse> recordVitals(
            @PathVariable Long id, @Valid @RequestBody RecordVitalsRequest request) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.recordVitals(id, request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
}
