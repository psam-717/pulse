package com.example.demo.controller;

import com.example.demo.model.Hospital;
import com.example.demo.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final BookingService bookingService;

    public HospitalController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<List<Hospital>> listHospitals() {
        return ResponseEntity.ok(bookingService.listHospitals());
    }
}
