package com.example.demo.controller;

import com.example.demo.model.Doctor;
import com.example.demo.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DoctorController {

    private final BookingService bookingService;

    public DoctorController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @SecurityRequirements()
    @GetMapping("/{departmentId}/doctors")
    public ResponseEntity<List<Doctor>> listDoctors(@PathVariable Long departmentId) {
        return ResponseEntity.ok(bookingService.listDoctors(departmentId));
    }
}
