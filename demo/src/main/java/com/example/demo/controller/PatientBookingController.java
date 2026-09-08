package com.example.demo.controller;

import com.example.demo.dto.PatientBookingResponse;
import com.example.demo.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Patient's own bookings (mobile "Bookings" section: pending/approved/
 * cancelled + payment). Patient JWT only.
 */
@RestController
@RequestMapping("/api/patients/me/bookings")
@PreAuthorize("hasRole('PATIENT')")
public class PatientBookingController {

    private final AppointmentService appointmentService;

    public PatientBookingController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    public ResponseEntity<List<PatientBookingResponse>> myBookings() {
        return ResponseEntity.ok(appointmentService.patientBookings(currentPatientId()));
    }
}
