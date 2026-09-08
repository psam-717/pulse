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
 * Patient's own appointments with FACILITY-plane status (approved/cancelled
 * via appointmentStatus) + payment — the mobile Bookings section source.
 *
 * Distinct from the legacy paged GET /patients/me/bookings (BookingController
 * legacy statuses); both coexist.
 */
@RestController
@RequestMapping("/api/patients/me/appointments")
@PreAuthorize("hasRole('PATIENT')")
public class PatientAppointmentsController {

    private final AppointmentService appointmentService;

    public PatientAppointmentsController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    public ResponseEntity<List<PatientBookingResponse>> myAppointments() {
        return ResponseEntity.ok(appointmentService.patientBookings(currentPatientId()));
    }
}
