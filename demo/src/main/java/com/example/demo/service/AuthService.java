package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.ForgotPasswordResponse;
import com.example.demo.dto.PasswordResetVerifyResponse;
import com.example.demo.dto.PatientLoginRequest;
import com.example.demo.dto.ResetPasswordRequest;
import com.example.demo.util.GhanaPhoneValidator;
import com.example.demo.dto.SignupRequest;
import com.example.demo.dto.VerifyOtpRequest;
import com.example.demo.model.Patient;
import com.example.demo.model.PasswordResetOtp;
import com.example.demo.model.PendingRegistration;
import com.example.demo.repository.PasswordResetOtpRepository;
import com.example.demo.repository.PatientRepository;
import com.example.demo.repository.PendingRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESET_TOKEN_TTL_MINUTES = 15;

    private final PendingRegistrationRepository pendingRepo;
    private final PatientRepository patientRepository;
    private final PasswordResetOtpRepository resetOtpRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ArkeselSmsService smsService;
    /** Verified against when the identifier is unknown so both login failure
     *  paths do ~equal bcrypt work (no timing side-channel for enumeration). */
    private final String dummyPasswordHash;

    private final int otpExpiryMinutes;
    private final int otpMaxAttempts;
    private final boolean otpDevMode;

    public AuthService(PendingRegistrationRepository pendingRepo,
                       PatientRepository patientRepository,
                       PasswordResetOtpRepository resetOtpRepository,
                       JwtUtil jwtUtil,
                       ArkeselSmsService smsService,
                       @Value("${otp.expiry-minutes:5}") int otpExpiryMinutes,
                       @Value("${otp.max-attempts:5}") int otpMaxAttempts,
                       @Value("${otp.dev-mode:true}") boolean otpDevMode) {
        this.pendingRepo = pendingRepo;
        this.patientRepository = patientRepository;
        this.resetOtpRepository = resetOtpRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.dummyPasswordHash = this.passwordEncoder.encode("pulse-invalid-password-placeholder");
        this.jwtUtil = jwtUtil;
        this.smsService = smsService;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpDevMode = otpDevMode;
    }

    @Transactional
    public void initiateSignup(SignupRequest request) {
        String phone = GhanaPhoneValidator.requireValid(request.phone(), "Phone number");

        // Replace any existing pending registration for this number.
        // Derived delete = executes its DELETE immediately (not deferred to
        // flush), so the new INSERT can't collide with the old row's unique
        // phone (PITFALLS 3.13 — the entity delete + insert ordering bug).
        pendingRepo.deleteByPhone(phone);

        String otp = generateOtp();
        String hashedPassword = passwordEncoder.encode(request.password());
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        PendingRegistration pending = new PendingRegistration(
                request.fullName(), phone, request.ghanaCard(),
                hashedPassword, otp, expiresAt
        );
        pendingRepo.save(pending);

        smsService.sendOtp(phone, otp, ArkeselSmsService.Purpose.VERIFICATION, otpExpiryMinutes);
    }

    @Transactional
    public void verifyOtpAndCreatePatient(VerifyOtpRequest request) {
        PendingRegistration pending = pendingRepo.findByPhone(request.phone())
                .orElseThrow(() -> new IllegalArgumentException("No pending registration for this number"));

        if (LocalDateTime.now().isAfter(pending.getExpiresAt())) {
            pendingRepo.delete(pending);
            throw new IllegalStateException("OTP has expired. Please sign up again.");
        }

        if (!pending.getOtp().equals(request.otp())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        // OTP verified — create the patient account
        String[] nameParts = pending.getFullName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Patient patient = new Patient();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setPhone(pending.getPhone());
        patient.setGhanaCard(pending.getGhanaCard());
        patient.setPassword(pending.getHashedPassword());
        // Every patient gets a number at creation — the profile DTO exposes
        // patientNumber as the public id, and login accepts it as identifier.
        // (Previously only the staff create path assigned one; self-signups
        // were left with a null number.)
        patient.setPatientNumber(PatientService.nextPatientNumber(patientRepository));
        patientRepository.save(patient);

        // Clean up the pending registration
        pendingRepo.delete(pending);
    }

    public AuthResponse patientLogin(PatientLoginRequest request) {
        String identifier = request.identifier();

        // Try to find by phone first, then ghanaCard, then patient number
        Patient patient = patientRepository.findByPhone(identifier)
                .or(() -> patientRepository.findByGhanaCard(identifier))
                .or(() -> patientRepository.findByPatientNumber(identifier))
                .orElse(null);

        // Uniform error + equalized work: an unknown identifier still runs the
        // bcrypt check against a dummy hash, so both failure paths behave
        // identically (message AND timing). Distinct messages leaked which
        // part failed → patient-ID enumeration (bug-triage BE-1).
        String storedHash = patient != null ? patient.getPassword() : dummyPasswordHash;
        if (!passwordEncoder.matches(request.password(), storedHash)) {
            throw new IllegalArgumentException("Invalid patient ID or password");
        }

        String token = jwtUtil.generateToken(patient.getId(), "PATIENT");
        return new AuthResponse(token, "PATIENT", patient.getId(), "Login successful");
    }

    /**
     * Legacy alias (BE-11 pre-#33): POST /api/auth/patient/forgot-password.
     * Same behavior as requestPasswordResetCode — issues a reset code.
     */
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        return requestPasswordResetCode(request.identifier());
    }

    /**
     * Step 1 of the password-reset flow (FE #33): POST /api/auth/patient/password-reset/request.
     *
     * <p>Response is deliberately IDENTICAL whether or not the identifier maps
     * to an account (uniform message, and {@code devOtp} only echoes in dev
     * mode), so this endpoint can't be used to enumerate patients (BE-1).
     */
    @Transactional
    public ForgotPasswordResponse requestPasswordResetCode(String identifierRaw) {
        String identifier = identifierRaw == null ? "" : identifierRaw.trim();
        Patient patient = findPatientByIdentifier(identifier);

        if (patient != null && patient.getPhone() != null) {
            // Replace any previous code for this patient (single active code)
            resetOtpRepository.deleteByPhone(patient.getPhone());
            String otp = generateOtp();
            resetOtpRepository.save(new PasswordResetOtp(
                    patient.getPhone(), otp,
                    LocalDateTime.now().plusMinutes(otpExpiryMinutes)));

            smsService.sendOtp(patient.getPhone(), otp,
                    ArkeselSmsService.Purpose.RESET, otpExpiryMinutes);

            return new ForgotPasswordResponse(
                    "If an account exists for this identifier, a password reset code has been sent.",
                    otpDevMode ? otp : null);
        }

        return new ForgotPasswordResponse(
                "If an account exists for this identifier, a password reset code has been sent.",
                null);
    }

    /**
     * Step 2 — verify the reset code and set the new password.
     *
     * Deliberately NOT @Transactional: the attempt counter must persist even
     * when this method throws (a rollback would undo the increment and the
     * lockout could never trigger — same trap as the staff login OTP). Each
     * repository save commits on its own.
     */
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        String identifier = request.identifier() == null ? "" : request.identifier().trim();
        Patient patient = findPatientByIdentifier(identifier);
        if (patient == null || patient.getPhone() == null) {
            // Same error as an unknown code — never reveal whether the
            // identifier exists (BE-1).
            throw new IllegalArgumentException("Reset code is invalid or expired. Request a new one.");
        }

        PasswordResetOtp otp = resetOtpRepository
                .findFirstByPhoneOrderByCreatedAtDesc(patient.getPhone())
                .filter(o -> !o.isUsed())
                .filter(o -> o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reset code is invalid or expired. Request a new one."));

        if (otp.getAttempts() >= otpMaxAttempts) {
            otp.setUsed(true);
            resetOtpRepository.save(otp);
            throw new IllegalArgumentException("Too many failed attempts. Request a new reset code.");
        }

        if (!otp.getCode().equals(request.otp() == null ? "" : request.otp().trim())) {
            otp.setAttempts(otp.getAttempts() + 1);
            resetOtpRepository.save(otp);
            int remaining = otpMaxAttempts - otp.getAttempts();
            throw new IllegalArgumentException(
                    "Invalid reset code." + (remaining > 0
                            ? " " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining."
                            : " Request a new one."));
        }

        patient.setPassword(passwordEncoder.encode(requireValidPassword(request.newPassword())));
        patientRepository.save(patient);

        otp.setUsed(true);
        resetOtpRepository.save(otp);

        return ApiResponse.success("Password reset successful. You can now log in with your new password.");
    }

    /**
     * Step 2 of the password-reset flow (FE #33): POST /api/auth/patient/password-reset/verify.
     * Validates the 6-digit code and exchanges it for a high-entropy resetToken
     * the confirm step can use WITHOUT re-asking for the code.
     *
     * <p>Deliberately NOT @Transactional — the attempt counter must persist even
     * when this method throws (same trap as resetPassword).
     */
    public PasswordResetVerifyResponse verifyPasswordResetCode(String identifierRaw, String code) {
        String identifier = identifierRaw == null ? "" : identifierRaw.trim();
        Patient patient = findPatientByIdentifier(identifier);
        if (patient == null || patient.getPhone() == null) {
            // Same error as an unknown code — never reveal whether the
            // identifier exists (BE-1).
            throw new IllegalArgumentException("Reset code is invalid or expired. Request a new one.");
        }

        PasswordResetOtp otp = resetOtpRepository
                .findFirstByPhoneOrderByCreatedAtDesc(patient.getPhone())
                .filter(o -> !o.isUsed())
                .filter(o -> o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reset code is invalid or expired. Request a new one."));

        if (otp.getAttempts() >= otpMaxAttempts) {
            otp.setUsed(true);
            resetOtpRepository.save(otp);
            throw new IllegalArgumentException("Too many failed attempts. Request a new reset code.");
        }

        if (!otp.getCode().equals(code == null ? "" : code.trim())) {
            otp.setAttempts(otp.getAttempts() + 1);
            resetOtpRepository.save(otp);
            int remaining = otpMaxAttempts - otp.getAttempts();
            throw new IllegalArgumentException(
                    "Invalid reset code." + (remaining > 0
                            ? " " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining."
                            : " Request a new one."));
        }

        // Code verified → consume it and mint the reset capability.
        otp.setUsed(true);
        otp.setResetToken(generateResetToken());
        otp.setTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        otp.setTokenUsed(false);
        resetOtpRepository.save(otp);

        return new PasswordResetVerifyResponse(otp.getResetToken());
    }

    /**
     * Step 3 of the password-reset flow (FE #33): POST /api/auth/patient/password-reset/confirm.
     * Sets the new password for the resetToken minted by the verify step.
     */
    public ApiResponse confirmPasswordReset(String identifierRaw, String resetTokenRaw, String newPassword) {
        String identifier = identifierRaw == null ? "" : identifierRaw.trim();
        String token = resetTokenRaw == null ? "" : resetTokenRaw.trim();
        Patient patient = findPatientByIdentifier(identifier);
        if (patient == null || patient.getPhone() == null || token.isEmpty()) {
            throw new IllegalArgumentException("Reset code is invalid or expired. Request a new one.");
        }

        PasswordResetOtp otp = resetOtpRepository
                .findFirstByPhoneAndResetTokenOrderByCreatedAtDesc(patient.getPhone(), token)
                .filter(o -> o.isUsed())
                .filter(o -> !Boolean.TRUE.equals(o.getTokenUsed()))
                .filter(o -> o.getTokenExpiresAt() != null
                        && o.getTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reset code is invalid or expired. Request a new one."));

        patient.setPassword(passwordEncoder.encode(requireStrongPassword(newPassword)));
        patientRepository.save(patient);

        otp.setTokenUsed(true);
        resetOtpRepository.save(otp);

        return ApiResponse.success("Password reset successful. You can now log in with your new password.");
    }

    /**
     * POST /api/auth/patient/resend-otp — re-issues the signup OTP for a pending
     * registration. Uniform 200 whether or not a registration exists (no
     * phone-number enumeration via the resend path).
     */
    public ApiResponse resendSignupOtp(String phoneRaw) {
        String phone = GhanaPhoneValidator.requireValid(
                phoneRaw == null ? "" : phoneRaw.trim(), "Phone number");
        pendingRepo.findByPhone(phone).ifPresent(pending -> {
            pending.setOtp(generateOtp());
            pending.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            pendingRepo.save(pending);
            smsService.sendOtp(phone, pending.getOtp(),
                    ArkeselSmsService.Purpose.VERIFICATION, otpExpiryMinutes);
        });
        return ApiResponse.success(
                "If a pending registration exists for this number, a new code has been sent.");
    }

    /** Password rule mirroring the FE #33 new-password screen: ≥8 chars + a letter + a digit. */
    private String requireStrongPassword(String password) {
        String p = requireValidPassword(password);
        if (!p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw new IllegalArgumentException(
                    "New password must include a letter and a number.");
        }
        return p;
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Resolve the login identifier (phone | Ghana Card | patient number) to a patient. */
    private Patient findPatientByIdentifier(String identifier) {
        return patientRepository.findByPhone(identifier)
                .or(() -> patientRepository.findByGhanaCard(identifier))
                .or(() -> patientRepository.findByPatientNumber(identifier))
                .orElse(null);
    }

    private String requireValidPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        return password;
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(999999));
    }
}