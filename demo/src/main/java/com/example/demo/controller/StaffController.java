package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.CreateStaffRequest;
import com.example.demo.dto.StaffResponse;
import com.example.demo.dto.UpdateStaffRequest;
import com.example.demo.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Facility-plane staff API (BACKEND_SPEC.md §6.5) — web dashboard.
 * Tenant-scoped by the staff token's facilityId claim; writes require the
 * admin role (permission matrix §5.8: Staff & Doctors = edit for admin only).
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<List<StaffResponse>> list() {
        return ResponseEntity.ok(staffService.list(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(staffService.get(SecurityUtils.requireFacilityId(), id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.create(SecurityUtils.requireFacilityId(), request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateStaffRequest request) {
        return ResponseEntity.ok(staffService.update(SecurityUtils.requireFacilityId(), id, request));
    }
}
