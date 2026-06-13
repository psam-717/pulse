package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.service.BookingService;
import com.example.demo.service.HospitalService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final BookingService bookingService;
    private final HospitalService hospitalService;

    public HospitalController(BookingService bookingService,
                              HospitalService hospitalService) {
        this.bookingService = bookingService;
        this.hospitalService = hospitalService;
    }

    // ===== Registration & Auth =====

    @SecurityRequirements()
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody HospitalRequest request) {
        RegistrationResponse response = hospitalService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @SecurityRequirements()
        @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody HospitalLoginRequest request) {
        return ResponseEntity.ok(hospitalService.login(request));
    }

    // ===== Public Discovery =====

    @SecurityRequirements()
        @GetMapping
    public ResponseEntity<List<Hospital>> listHospitals() {
        return ResponseEntity.ok(bookingService.listHospitals());
    }

    @SecurityRequirements()
        @GetMapping("/{hospitalId}")
        public ResponseEntity<HospitalResponse> getHospital(@PathVariable Long hospitalId) {
        return ResponseEntity.ok(
                hospitalService.toResponse(hospitalService.getHospitalById(hospitalId)));
    }

    // ===== Department Management (Hospital Admin / Super Admin) =====

    @PostMapping("/{hospitalId}/departments")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Department> createDepartment(
            @PathVariable Long hospitalId,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hospitalService.createDepartment(hospitalId, request));
    }

    @DeleteMapping("/{hospitalId}/departments/{departmentId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse> deleteDepartment(
            @PathVariable Long hospitalId,
            @PathVariable Long departmentId) {
        hospitalService.deleteDepartment(hospitalId, departmentId);
        return ResponseEntity.ok(ApiResponse.success("Department deleted"));
    }

    // ===== Working Hours Management (Hospital Admin / Super Admin) =====

    @PutMapping("/{hospitalId}/working-hours")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<WorkingHours>> configureWorkingHours(
            @PathVariable Long hospitalId,
            @Valid @RequestBody WorkingHoursRequest request) {
        return ResponseEntity.ok(
                hospitalService.configureWorkingHours(hospitalId, request));
    }

    @SecurityRequirements()
        @GetMapping("/{hospitalId}/working-hours")
        public ResponseEntity<List<WorkingHours>> getWorkingHours(@PathVariable Long hospitalId) {
            return ResponseEntity.ok(hospitalService.getWorkingHours(hospitalId));
        }

        // ===== License Document Management =====

        @PostMapping(value = "/{hospitalId}/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        public ResponseEntity<HospitalResponse> uploadLicense(
                @PathVariable Long hospitalId,
                @RequestParam("file") MultipartFile file,
                @AuthenticationPrincipal Long adminId) {
            return ResponseEntity.ok(hospitalService.uploadLicense(hospitalId, file, adminId));
        }

        @GetMapping("/{hospitalId}/license")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        public ResponseEntity<HospitalResponse> getLicenseInfo(@PathVariable Long hospitalId) {
            return ResponseEntity.ok(
                    hospitalService.toResponse(hospitalService.getHospitalById(hospitalId)));
        }

        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}