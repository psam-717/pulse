package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.BookingResponse;
import com.example.demo.dto.PatientResponse;
import com.example.demo.service.BookingService;
import com.example.demo.service.PatientService;
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
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<PatientResponse>> listPatients() {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<PatientResponse> getPatient(@PathVariable Long id) {
        SecurityUtils.requireFacilityId();
        return ResponseEntity.ok(patientService.get(id));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
}
