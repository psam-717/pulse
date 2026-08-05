package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.AssignHeadDoctorRequest;
import com.example.demo.dto.CreateDepartmentRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentStatsResponse;
import com.example.demo.dto.UpdateDepartmentRequest;
import com.example.demo.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Facility-plane department API (BACKEND_SPEC.md §6.2) — web dashboard.
 * Every request is tenant-scoped by the staff token's facilityId claim
 * (SecurityUtils.requireFacilityId). Writes require the admin role
 * (permission matrix §5.8: Departments = edit for admin only).
 *
 * NOTE: GET /api/departments/{departmentId}/doctors (mobile discovery)
 * lives in DoctorController — intentionally public.
 */
@RestController
@RequestMapping("/api/departments")
public class FacilityDepartmentController {

    private final DepartmentService departmentService;

    public FacilityDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> list() {
        return ResponseEntity.ok(departmentService.list(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/stats")
    public ResponseEntity<DepartmentStatsResponse> stats() {
        return ResponseEntity.ok(departmentService.stats(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.get(SecurityUtils.requireFacilityId(), id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.create(SecurityUtils.requireFacilityId(), request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.update(SecurityUtils.requireFacilityId(), id, request));
    }

    @PatchMapping("/{id}/head-doctor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> assignHeadDoctor(
            @PathVariable Long id,
            @Valid @RequestBody AssignHeadDoctorRequest request) {
        return ResponseEntity.ok(departmentService.assignHeadDoctor(
                SecurityUtils.requireFacilityId(), id, request.headDoctorName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        departmentService.delete(SecurityUtils.requireFacilityId(), id);
        return ResponseEntity.ok(ApiResponse.success("Department deleted"));
    }
}
