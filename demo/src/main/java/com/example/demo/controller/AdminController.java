package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.CreateDoctorRequest;
import com.example.demo.dto.DoctorLoginRequest;
import com.example.demo.model.Doctor;
import com.example.demo.service.DoctorAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminController {

    private final DoctorAdminService doctorAdminService;

    public AdminController(DoctorAdminService doctorAdminService) {
        this.doctorAdminService = doctorAdminService;
    }

    @SecurityRequirements()
    @PostMapping("/create-doctor")
    public ResponseEntity<ApiResponse> createDoctor(@RequestBody CreateDoctorRequest request) {
        Doctor doctor = doctorAdminService.createDoctor(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Doctor created with workspace ID: " + doctor.getWorkspaceId()
        ));
    }

    @SecurityRequirements()
        @PostMapping("/login")
        public ResponseEntity<AuthResponse> login(@RequestBody DoctorLoginRequest request) {
        return ResponseEntity.ok(doctorAdminService.doctorLogin(request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}