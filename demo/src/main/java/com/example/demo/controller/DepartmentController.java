package com.example.demo.controller;

import com.example.demo.model.Department;
import com.example.demo.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hospitals")
public class DepartmentController {

    private final BookingService bookingService;

    public DepartmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @SecurityRequirements()
    @GetMapping("/{hospitalId}/departments")
    public ResponseEntity<Page<Department>> listDepartments(
            @PathVariable Long hospitalId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(bookingService.listDepartments(hospitalId, pageable));
    }
}
