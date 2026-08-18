package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.VerifyLoginOtpRequest;
import com.example.demo.dto.WorkspaceSessionResponse;
import com.example.demo.model.LoginOtp;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.repository.LoginOtpRepository;
import com.example.demo.repository.StaffMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Facility-plane staff authentication (web dashboard) with a 2FA step:
 *
 *   1. POST /api/auth/login (email+password)  → validates credentials,
 *      issues a single-use expiring OTP, returns { token: null, session }
 *   2. POST /api/auth/login/verify-otp (code) → issues the real JWT bound
 *      to the facilityId (BACKEND_SPEC §2.3)
 *
 * The legacy patient/hospital auth is untouched. Real OTP delivery (email/
 * SMS) is a TODO — dev mode logs the code and echoes it as devOtp.
 */
@Service
public class StaffAuthService {

    private static final Logger log = LoggerFactory.getLogger(StaffAuthService.class);

    private final StaffMemberRepository staffRepository;
    private final LoginOtpRepository loginOtpRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private final int otpExpiryMinutes;
    private final int otpMaxAttempts;
    private final boolean otpDevMode;

    public StaffAuthService(StaffMemberRepository staffRepository,
                            LoginOtpRepository loginOtpRepository,
                            JwtUtil jwtUtil,
                            @Value("${otp.expiry-minutes:5}") int otpExpiryMinutes,
                            @Value("${otp.max-attempts:5}") int otpMaxAttempts,
                            @Value("${otp.dev-mode:true}") boolean otpDevMode) {
        this.staffRepository = staffRepository;
        this.loginOtpRepository = loginOtpRepository;
        this.jwtUtil = jwtUtil;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpDevMode = otpDevMode;
    }

    /** Step 1 — validate credentials, issue the OTP, return no token yet. */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        StaffMember staff = findActiveStaff(email, request.password());

        // Replace any previous code for this account (single active code)
        loginOtpRepository.deleteByEmail(email);
        String otp = generateOtp();
        loginOtpRepository.save(new LoginOtp(email, otp,
                LocalDateTime.now().plusMinutes(otpExpiryMinutes)));

        // TODO: replace with real delivery (email provider / SMS). Dev mode
        // logs the code so manual testing works end-to-end.
        log.info("LOGIN OTP for {}: {} (expires in {} min)", email, otp, otpExpiryMinutes);

        WorkspaceSessionResponse session = WorkspaceSessionResponse.from(staff);
        return new LoginResponse(null, session.role(), staff.getId(),
                "Verification code sent to " + email, session,
                otpDevMode ? otp : null);
    }

    /**
     * Step 2 — verify the code and issue the real facility-plane JWT.
     *
     * Deliberately NOT @Transactional: the attempt counter must persist even
     * when this method throws (a rollback would undo the increment and the
     * lockout could never trigger). Each repository save commits on its own.
     */
    public LoginResponse verifyLoginOtp(VerifyLoginOtpRequest request) {
        String email = request.email().trim().toLowerCase();
        LoginOtp otp = loginOtpRepository.findFirstByEmailOrderByCreatedAtDesc(email)
                .filter(o -> !o.isUsed())
                .filter(o -> o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Verification code is invalid or expired. Request a new one."));

        if (otp.getAttempts() >= otpMaxAttempts) {
            otp.setUsed(true);
            loginOtpRepository.save(otp);
            throw new IllegalArgumentException(
                    "Too many failed attempts. Request a new verification code.");
        }

        if (!otp.getCode().equals(request.code().trim())) {
            otp.setAttempts(otp.getAttempts() + 1);
            loginOtpRepository.save(otp);
            int remaining = otpMaxAttempts - otp.getAttempts();
            throw new IllegalArgumentException(
                    "Invalid verification code." + (remaining > 0
                            ? " " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining."
                            : " Request a new one."));
        }

        otp.setUsed(true);
        loginOtpRepository.save(otp);

        StaffMember staff = staffRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        String token = jwtUtil.generateStaffToken(
                staff.getId(), staff.getFacilityId(), staff.getRole().name());
        WorkspaceSessionResponse session = WorkspaceSessionResponse.from(staff);
        return new LoginResponse(token, session.role(), staff.getId(),
                "Login successful", session, null);
    }

    /** Resolves the session for an authenticated staff member (GET /auth/me). */
    public WorkspaceSessionResponse me(Long staffId) {
        StaffMember staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        return WorkspaceSessionResponse.from(staff);
    }

    // ===== Helpers =====

    private StaffMember findActiveStaff(String email, String rawPassword) {
        StaffMember staff = staffRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(rawPassword, staff.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (staff.getAccountStatus() == StaffAccountStatus.DEACTIVATED) {
            throw new IllegalArgumentException("This account has been deactivated");
        }
        return staff;
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
