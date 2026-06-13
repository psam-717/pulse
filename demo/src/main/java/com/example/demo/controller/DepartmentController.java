package com.example.demo.controller;

import com.example.demo.model.Department;
import com.example.demo.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
public class DepartmentController {

    private final BookingService bookingService;

    public DepartmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @SecurityRequirements()
    @GetMapping("/{hospitalId}/departments")
    public ResponseEntity<List<Department>> listDepartments(@PathVariable Long hospitalId) {
        return ResponseEntity.ok(bookingService.listDepartments(hospitalId));
    }
}
