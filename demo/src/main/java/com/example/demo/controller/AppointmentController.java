package com.example.demo.controller;

import com.example.demo.config.SecurityUtils;
import com.example.demo.dto.AppointmentDepartmentResponse;
import com.example.demo.dto.AppointmentResponse;
import com.example.demo.dto.AppointmentStatsResponse;
import com.example.demo.dto.UpdateAppointmentStatusRequest;
import com.example.demo.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Facility-plane appointment API (BACKEND_SPEC.md §6.1) — web dashboard.
 * GET /appointments serves two shapes: the day view (?date&departmentId?&status?)
 * and the range view (?from&to) — dispatched on which params are present.
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> list(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long facilityId = SecurityUtils.requireFacilityId();
        if (from != null && to != null) {
            return ResponseEntity.ok(appointmentService.listForRange(
                    facilityId, parseDate(from, "from"), parseDate(to, "to")));
        }
        LocalDate day = date != null ? parseDate(date, "date") : LocalDate.now();
        return ResponseEntity.ok(appointmentService.listForDay(facilityId, day, departmentId, status));
    }

    @GetMapping("/stats")
    public ResponseEntity<AppointmentStatsResponse> stats(
            @RequestParam(required = false) String date) {
        Long facilityId = SecurityUtils.requireFacilityId();
        LocalDate day = date != null ? parseDate(date, "date") : LocalDate.now();
        return ResponseEntity.ok(appointmentService.statsForDay(facilityId, day));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<AppointmentDepartmentResponse>> departments() {
        return ResponseEntity.ok(appointmentService.departments(SecurityUtils.requireFacilityId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'FRONT_DESK')")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return ResponseEntity.ok(appointmentService.updateStatus(
                SecurityUtils.requireFacilityId(), id, request.status()));
    }

    private static LocalDate parseDate(String value, String param) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid " + param + " '" + value + "'. Expected format YYYY-MM-DD");
        }
    }
}
