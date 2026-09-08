package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.dashboard.DashboardAlertResponse;
import com.example.demo.dto.dashboard.DashboardDepartmentQueueResponse;
import com.example.demo.dto.dashboard.StatMetricResponse;
import com.example.demo.dto.dashboard.VolumePointResponse;
import com.example.demo.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin dashboard reads (BACKEND_SPEC.md §6.8) — every aggregate is
 * facility-scoped from the caller's JWT (§2.2).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<StatMetricResponse>> stats() {
        return ResponseEntity.ok(dashboardService.stats(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<DashboardDepartmentQueueResponse>> queue() {
        return ResponseEntity.ok(dashboardService.queue(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<DashboardAlertResponse>> alerts() {
        return ResponseEntity.ok(dashboardService.alerts(SecurityUtils.requireFacilityId()));
    }

    @GetMapping("/patient-volume")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<List<VolumePointResponse>> patientVolume() {
        return ResponseEntity.ok(dashboardService.patientVolume(SecurityUtils.requireFacilityId()));
    }
}
