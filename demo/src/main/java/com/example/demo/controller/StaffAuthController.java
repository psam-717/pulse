package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.StaffPasswordResetConfirmRequest;
import com.example.demo.dto.StaffPasswordResetRequest;
import com.example.demo.dto.StaffPasswordResetVerifyRequest;
import com.example.demo.dto.StaffPasswordResetVerifyResponse;
import com.example.demo.dto.VerifyLoginOtpRequest;
import com.example.demo.dto.WorkspaceSessionResponse;
import com.example.demo.service.StaffAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Facility-plane staff auth — login (2FA) + session resolution (web dashboard). */
@RestController
@RequestMapping("/api/auth")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(staffAuthService.login(request, userAgent));
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<LoginResponse> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(staffAuthService.verifyLoginOtp(request, userAgent));
    }

    @GetMapping("/me")
    public ResponseEntity<WorkspaceSessionResponse> me(@AuthenticationPrincipal Long staffId) {
        return ResponseEntity.ok(staffAuthService.me(staffId));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(
            @Valid @RequestBody StaffPasswordResetRequest request) {
        return ResponseEntity.ok(staffAuthService.requestStaffPasswordReset(request));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<StaffPasswordResetVerifyResponse> verifyPasswordReset(
            @Valid @RequestBody StaffPasswordResetVerifyRequest request) {
        return ResponseEntity.ok(staffAuthService.verifyStaffPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(
            @Valid @RequestBody StaffPasswordResetConfirmRequest request) {
        return ResponseEntity.ok(staffAuthService.confirmStaffPasswordReset(request));
    }
}
