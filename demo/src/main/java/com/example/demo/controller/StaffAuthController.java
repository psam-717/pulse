package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.VerifyLoginOtpRequest;
import com.example.demo.dto.WorkspaceSessionResponse;
import com.example.demo.service.StaffAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Facility-plane staff auth — login (2FA) + session resolution (web dashboard). */
@RestController
@RequestMapping("/api/auth")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(staffAuthService.login(request));
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<LoginResponse> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request) {
        return ResponseEntity.ok(staffAuthService.verifyLoginOtp(request));
    }

    @GetMapping("/me")
    public ResponseEntity<WorkspaceSessionResponse> me(@AuthenticationPrincipal Long staffId) {
        return ResponseEntity.ok(staffAuthService.me(staffId));
    }
}
