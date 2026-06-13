package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.PatientLoginRequest;
import com.example.demo.dto.SignupRequest;
import com.example.demo.dto.VerifyOtpRequest;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/patient")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @SecurityRequirements()
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signup(@RequestBody SignupRequest request) {
        authService.initiateSignup(request);
        return ResponseEntity.ok(ApiResponse.success(
                        "OTP sent to " + request.phone() + ". Please verify to complete registration."
                ));
    }

    @SecurityRequirements()
        @PostMapping("/verify-otp")
        public ResponseEntity<ApiResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        authService.verifyOtpAndCreatePatient(request);
        return ResponseEntity.ok(ApiResponse.success(
                        "Phone verified. Account created successfully."
                ));
    }

    @SecurityRequirements()
        @PostMapping("/login")
        public ResponseEntity<AuthResponse> login(@RequestBody PatientLoginRequest request) {
        return ResponseEntity.ok(authService.patientLogin(request));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}