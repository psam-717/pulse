package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.ForgotPasswordResponse;
import com.example.demo.dto.PasswordResetConfirmRequest;
import com.example.demo.dto.PasswordResetVerifyRequest;
import com.example.demo.dto.PasswordResetVerifyResponse;
import com.example.demo.dto.PatientLoginRequest;
import com.example.demo.dto.ResendOtpRequest;
import com.example.demo.dto.ResetPasswordRequest;
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

    @SecurityRequirements()
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @SecurityRequirements()
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    // --- FE #33 password-reset flow (3-step) ---

    @SecurityRequirements()
    @PostMapping("/password-reset/request")
    public ResponseEntity<ForgotPasswordResponse> requestPasswordReset(
            @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.requestPasswordResetCode(request.identifier()));
    }

    @SecurityRequirements()
    @PostMapping("/password-reset/verify")
    public ResponseEntity<PasswordResetVerifyResponse> verifyPasswordReset(
            @RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyPasswordResetCode(request.identifier(), request.code()));
    }

    @SecurityRequirements()
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse> confirmPasswordReset(
            @RequestBody PasswordResetConfirmRequest request) {
        return ResponseEntity.ok(authService.confirmPasswordReset(
                request.identifier(), request.resetToken(), request.newPassword()));
    }

    @SecurityRequirements()
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse> resendOtp(@RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendSignupOtp(request.phone()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}