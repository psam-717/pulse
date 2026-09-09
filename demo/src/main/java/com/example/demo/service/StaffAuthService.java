package com.example.demo.service;

import com.example.demo.config.JwtUtil;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.StaffPasswordResetConfirmRequest;
import com.example.demo.dto.StaffPasswordResetRequest;
import com.example.demo.dto.StaffPasswordResetVerifyRequest;
import com.example.demo.dto.StaffPasswordResetVerifyResponse;
import com.example.demo.dto.VerifyLoginOtpRequest;
import com.example.demo.dto.WorkspaceSessionResponse;
import com.example.demo.model.LoginOtp;
import com.example.demo.model.StaffAccountStatus;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffPasswordResetOtp;
import com.example.demo.repository.LoginOtpRepository;
import com.example.demo.repository.StaffMemberRepository;
import com.example.demo.repository.StaffPasswordResetOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final StaffPasswordResetOtpRepository staffResetOtpRepository;
    private final ResendEmailService resendEmailService;
    private final JwtUtil jwtUtil;
    private final AccountSettingsService accountSettingsService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int RESET_TOKEN_TTL_MINUTES = 15;

    private final int otpExpiryMinutes;
    private final int otpMaxAttempts;
    private final boolean otpDevMode;

    public StaffAuthService(StaffMemberRepository staffRepository,
                            LoginOtpRepository loginOtpRepository,
                            StaffPasswordResetOtpRepository staffResetOtpRepository,
                            ResendEmailService resendEmailService,
                            JwtUtil jwtUtil,
                            AccountSettingsService accountSettingsService,
                            @Value("${otp.expiry-minutes:5}") int otpExpiryMinutes,
                            @Value("${otp.max-attempts:5}") int otpMaxAttempts,
                            @Value("${otp.dev-mode:true}") boolean otpDevMode) {
        this.staffRepository = staffRepository;
        this.loginOtpRepository = loginOtpRepository;
        this.staffResetOtpRepository = staffResetOtpRepository;
        this.resendEmailService = resendEmailService;
        this.jwtUtil = jwtUtil;
        this.accountSettingsService = accountSettingsService;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpDevMode = otpDevMode;
    }

    /** Step 1 — validate credentials. If the account has 2FA enabled, issue
     *  the OTP and return no token yet; otherwise return the real JWT now
     *  (per-account toggle /settings/2fa). */
    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();
        StaffMember staff = findActiveStaff(email, request.password());

        if (!staff.isTwoFactorEnabled()) {
            String sid = accountSettingsService.registerSession(staff.getId(), userAgent);
            String token = jwtUtil.generateStaffToken(
                    staff.getId(), staff.getFacilityId(), staff.getRole().name(), sid);
            WorkspaceSessionResponse session = WorkspaceSessionResponse.from(staff);
            return new LoginResponse(token, session.role(), staff.getId(),
                    "Login successful", session, null);
        }

        // 2FA enabled: replace any previous code for this account (single
        // active code)
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
    public LoginResponse verifyLoginOtp(VerifyLoginOtpRequest request, String userAgent) {
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
        String sid = accountSettingsService.registerSession(staff.getId(), userAgent);
        String token = jwtUtil.generateStaffToken(
                staff.getId(), staff.getFacilityId(), staff.getRole().name(), sid);
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

    // ===== Staff password reset (web forgot-password, email delivery) =====

    /**
     * Step 1 — request a reset code for a staff work email. Anti-enumeration:
     * the response is uniform whether or not the account exists; dev mode
     * additionally echoes the code (devOtp) when the account DOES exist so
     * hand-tests work before the email channel is validated.
     */
    @Transactional
    public Map<String, Object> requestStaffPasswordReset(StaffPasswordResetRequest request) {
        String email = request.email().trim().toLowerCase();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",
                "If an account exists for this email, a reset code has been sent.");

        StaffMember staff = staffRepository.findByEmail(email).orElse(null);
        if (staff == null) {
            return response; // uniform response — no existence oracle
        }

        // Single active code per email (re-request replaces the old one).
        staffResetOtpRepository.deleteByEmail(email);
        String otp = generateOtp();
        staffResetOtpRepository.save(new StaffPasswordResetOtp(email, otp,
                LocalDateTime.now().plusMinutes(otpExpiryMinutes)));

        resendEmailService.sendPasswordResetCode(email, otp, otpExpiryMinutes);

        if (otpDevMode) {
            response.put("devOtp", otp);
        }
        return response;
    }

    /**
     * Step 2 — verify the emailed code and issue the single-use reset token.
     *
     * Deliberately NOT @Transactional: the attempt counter must persist even
     * when this method throws (a rollback would undo the increment and the
     * lockout could never trigger). Each repository save commits on its own.
     */
    public StaffPasswordResetVerifyResponse verifyStaffPasswordReset(StaffPasswordResetVerifyRequest request) {
        String email = request.email().trim().toLowerCase();
        StaffPasswordResetOtp otp = staffResetOtpRepository.findFirstByEmailOrderByCreatedAtDesc(email)
                .filter(o -> !o.isUsed())
                .filter(o -> o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reset code is invalid or expired. Request a new one."));

        if (otp.getAttempts() >= otpMaxAttempts) {
            otp.setUsed(true);
            staffResetOtpRepository.save(otp);
            throw new IllegalArgumentException(
                    "Too many failed attempts. Request a new reset code.");
        }

        if (!otp.getCode().equals(request.code().trim())) {
            otp.setAttempts(otp.getAttempts() + 1);
            staffResetOtpRepository.save(otp);
            int remaining = otpMaxAttempts - otp.getAttempts();
            throw new IllegalArgumentException(
                    "Invalid reset code." + (remaining > 0
                            ? " " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining."
                            : " Request a new one."));
        }

        otp.setUsed(true);
        otp.setResetToken(generateResetTokenHex());
        otp.setTokenExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        otp.setTokenUsed(false);
        staffResetOtpRepository.save(otp);

        return new StaffPasswordResetVerifyResponse(otp.getResetToken());
    }

    /**
     * Step 3 — confirm with the token and set the new password. Consumes the
     * token, invalidates any pending login/reset codes for this email, and
     * leaves existing sessions to expire naturally (they still authenticate).
     */
    @Transactional
    public Map<String, Object> confirmStaffPasswordReset(StaffPasswordResetConfirmRequest request) {
        String email = request.email().trim().toLowerCase();

        StaffMember staff = staffRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "We could not reset this password. Request a new code and try again."));

        StaffPasswordResetOtp otp = staffResetOtpRepository
                .findFirstByEmailAndResetTokenOrderByCreatedAtDesc(email, request.resetToken())
                .filter(o -> o.getTokenExpiresAt() != null)
                .filter(o -> o.getTokenExpiresAt().isAfter(LocalDateTime.now()))
                .filter(o -> Boolean.FALSE.equals(o.getTokenUsed()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "This reset link is invalid or expired. Request a new code."));

        String raw = request.newPassword();
        if (raw == null || raw.length() < 8
                || !raw.matches(".*[A-Za-z].*") || !raw.matches(".*\\d.*")) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters and include a letter and a number.");
        }

        staff.setPassword(passwordEncoder.encode(raw));
        staffRepository.save(staff);

        otp.setTokenUsed(true);
        staffResetOtpRepository.save(otp);
        staffResetOtpRepository.deleteByEmail(email);
        loginOtpRepository.deleteByEmail(email); // invalidate any pending 2FA codes

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Password updated. You can sign in now.");
        return response;
    }

    // ===== Helpers =====

    private String generateResetTokenHex() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

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
