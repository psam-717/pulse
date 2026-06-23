package com.example.demo.controller;

import com.example.demo.model.Doctor;
import com.example.demo.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
public class DoctorController {

    private final BookingService bookingService;

    public DoctorController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @SecurityRequirements()
    @GetMapping("/{departmentId}/doctors")
    public ResponseEntity<Page<Doctor>> listDoctors(
            @PathVariable Long departmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(bookingService.listDoctors(departmentId, pageable));
    }
}
