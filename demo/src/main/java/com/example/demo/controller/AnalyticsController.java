package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.analytics.FacilityAnalyticsResponse;
import com.example.demo.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Facility analytics (BACKEND_SPEC.md §6.9). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','FRONT_DESK','READ_ONLY')")
    public ResponseEntity<FacilityAnalyticsResponse> analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        return ResponseEntity.ok(analyticsService.analytics(SecurityUtils.requireFacilityId(), start, end));
    }
}
