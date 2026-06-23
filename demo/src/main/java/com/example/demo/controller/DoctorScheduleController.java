package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.BookingResponse;
import com.example.demo.service.BookingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctors")
public class DoctorScheduleController {

    private final BookingService bookingService;

    public DoctorScheduleController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/me/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Page<BookingResponse>> listMyAppointments(
            @PageableDefault(size = 20) Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long doctorId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(bookingService.listDoctorAppointments(doctorId, pageable));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }
}
