package com.example.demo.controller;

import com.example.demo.model.TimeSlot;
import com.example.demo.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class TimeSlotController {

    private final BookingService bookingService;

    public TimeSlotController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @SecurityRequirements()
    @GetMapping("/{doctorId}/slots")
    public ResponseEntity<List<TimeSlot>> listAvailableSlots(
            @PathVariable Long doctorId,
            @RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date);
        return ResponseEntity.ok(bookingService.listAvailableSlots(doctorId, localDate));
    }
}
