package com.example.demo.controller;

import com.example.demo.dto.BookingSummaryResponse;
import com.example.demo.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Patient Payments screen source (ARCHITECTURE.md §8 P3 / G7).
 * Unpaid, not cancelled, not checked-in — soonest pay-by first.
 */
@RestController
@RequestMapping("/api/patients/me/outstanding")
public class PatientOutstandingController {

    private final BookingService bookingService;

    public PatientOutstandingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<BookingSummaryResponse>> listOutstanding() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long patientId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(bookingService.listOutstanding(patientId));
    }
}
