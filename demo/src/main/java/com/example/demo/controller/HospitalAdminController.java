package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.HospitalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/hospitals")
public class HospitalAdminController {

    private final HospitalService hospitalService;

    public HospitalAdminController(HospitalService hospitalService) {
        this.hospitalService = hospitalService;
    }

    @PutMapping("/{hospitalId}/verify")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<HospitalResponse> verifyLicense(
            @PathVariable Long hospitalId,
            @Valid @RequestBody LicenseVerifyRequest request) {
        return ResponseEntity.ok(hospitalService.verifyLicense(hospitalId, request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}