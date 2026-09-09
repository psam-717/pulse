package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.CreatePrescriptionRequest;
import com.example.demo.dto.CreateVisitNoteRequest;
import com.example.demo.dto.MedicalRecordsResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Hospital;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.HospitalRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.StaffMemberRepository;
import com.example.demo.service.PatientRecordsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Facility-plane clinical record access + authoring (backend issue #41).
 *
 * The patient-facing mobile app reads the same records via
 * GET /api/patients/me/records — this surface serves the web staff app:
 * read a patient's full record history (visits / lab / prescriptions) and
 * author consultation notes + prescriptions. Author context (department,
 * hospital, doctor display name) is resolved from the JWT session, never
 * from the request body; the doctor name is a display snapshot, which is
 * correct for medical history.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/records")
public class PatientClinicalRecordsController {

    private static final java.util.Set<String> WRITER_ROLES =
            java.util.Set.of("ADMIN", "DOCTOR", "NURSE", "FRONT_DESK");

    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final PatientRecordsService recordsService;

    public PatientClinicalRecordsController(PatientRepository patientRepository,
                                            HospitalRepository hospitalRepository,
                                            StaffMemberRepository staffMemberRepository,
                                            PatientRecordsService recordsService) {
        this.patientRepository = patientRepository;
        this.hospitalRepository = hospitalRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.recordsService = recordsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<MedicalRecordsResponse> getRecords(@PathVariable Long patientId) {
        requirePatient(patientId);
        return ResponseEntity.ok(recordsService.getRecords(patientId));
    }

    @PostMapping("/visits")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK')")
    public ResponseEntity<MedicalRecordsResponse.Visit> addVisit(
            @PathVariable Long patientId,
            @Valid @RequestBody CreateVisitNoteRequest request) {
        requirePatient(patientId);
        SessionContext ctx = sessionContext();
        MedicalRecordsResponse.Visit visit = recordsService.addVisit(
                patientId, ctx.department(), ctx.hospital(), parseDate(request.visitDate(), "visitDate"),
                ctx.doctorName(), request.summary(),
                request.symptoms(), request.recommendations());
        return ResponseEntity.ok(visit);
    }

    @PostMapping("/prescriptions")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK')")
    public ResponseEntity<MedicalRecordsResponse.Prescription> addPrescription(
            @PathVariable Long patientId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        requirePatient(patientId);
        SessionContext ctx = sessionContext();
        MedicalRecordsResponse.Prescription rx = recordsService.addPrescription(
                patientId, request.medication(), request.dose(),
                parseDate(request.prescribedDate(), "prescribedDate"),
                ctx.doctorName(), ctx.hospital(), request.instructions());
        return ResponseEntity.ok(rx);
    }

    // ===================== helpers =====================

    private void requirePatient(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found");
        }
    }

    private record SessionContext(String department, String hospital, String doctorName) {}

    private SessionContext sessionContext() {
        Long facilityId = SecurityUtils.requireFacilityId();
        Long staffId = SecurityUtils.requireStaffId();
        Hospital hospital = hospitalRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
        StaffMember staff = staffMemberRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        String department = staff.getDepartmentName() != null && !staff.getDepartmentName().isBlank()
                ? staff.getDepartmentName() : "General";
        return new SessionContext(department, hospital.getName(), staff.getName());
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + field + " '" + value + "'. Expected YYYY-MM-DD");
        }
    }
}
